package io.quarkiverse.aesh.deployment;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.AnnotationValue.Kind;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;

public class AeshNativeImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(AeshNativeImageProcessor.class);

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void reflectionConfiguration(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchies,
            BuildProducer<NativeImageProxyDefinitionBuildItem> nativeImageProxies) {
        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<DotName> annotationsToAnalyze = Arrays.asList(
                DotName.createSimple("org.aesh.command.CommandDefinition"),
                DotName.createSimple("org.aesh.command.GroupCommandDefinition"),
                DotName.createSimple("org.aesh.command.option.Option"),
                DotName.createSimple("org.aesh.command.option.OptionList"),
                DotName.createSimple("org.aesh.command.option.OptionGroup"),
                DotName.createSimple("org.aesh.command.option.Arguments"),
                DotName.createSimple("org.aesh.command.option.Argument"),
                DotName.createSimple("org.aesh.command.option.ParentCommand"));

        Set<ClassInfo> foundClasses = new HashSet<>();
        Set<Type> typeAnnotationValues = new HashSet<>();

        for (DotName analyzedAnnotation : annotationsToAnalyze) {
            for (AnnotationInstance ann : index.getAnnotations(analyzedAnnotation)) {
                AnnotationTarget target = ann.target();
                switch (target.kind()) {
                    case CLASS:
                        foundClasses.add(target.asClass());
                        break;
                    case FIELD:
                        foundClasses.add(target.asField().declaringClass());
                        break;
                    case METHOD:
                        foundClasses.add(target.asMethod().declaringClass());
                        break;
                    case METHOD_PARAMETER:
                        foundClasses.add(target.asMethodParameter().method().declaringClass());
                        break;
                    default:
                        LOGGER.warnf("Unsupported type %s annotated with %s", target.kind().name(),
                                analyzedAnnotation);
                        break;
                }

                // Register classes referenced in aesh annotations for reflection
                // (converter, completer, validator, activator, renderer, parser)
                List<AnnotationValue> values = ann.valuesWithDefaults(index);
                for (AnnotationValue value : values) {
                    if (value.kind() == Kind.CLASS) {
                        typeAnnotationValues.add(value.asClass());
                    } else if (value.kind() == Kind.ARRAY && value.componentKind() == Kind.CLASS) {
                        Collections.addAll(typeAnnotationValues, value.asClassArray());
                    }
                }
            }
        }

        // Register both declared methods and fields as they are accessed by aesh during initialization
        foundClasses.forEach(classInfo -> {
            if (Modifier.isInterface(classInfo.flags())) {
                nativeImageProxies
                        .produce(new NativeImageProxyDefinitionBuildItem(classInfo.name().toString()));
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(classInfo.name().toString())
                        .constructors(false).methods().fields().build());
            } else {
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(classInfo.name().toString())
                        .methods().fields().build());
            }
        });

        typeAnnotationValues.forEach(type -> reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem
                .builder(type)
                .source(AeshNativeImageProcessor.class.getSimpleName())
                .ignoreFieldPredicate(fi -> true)
                .ignoreMethodPredicate(mi -> true)
                .build()));
    }
}
