package io.renren.common.utils;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Collectors;


public class BeanFindUtils {

    private static ClassPathScanningCandidateComponentProvider scanner;

    
    public static Set<Class<?>> findAnnotation(String basePackage, Class<? extends Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = getInstance();
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return getClasses(basePackage, scanner);
    }

    
    public static Set<Class<?>> findInterface(String basePackage, Class<?> interfaceClass) {
        ClassPathScanningCandidateComponentProvider scanner = getInstance();
        scanner.addIncludeFilter(new AssignableTypeFilter(interfaceClass));
        return getClasses(basePackage, scanner);
    }


    private static Set<Class<?>> getClasses(String basePackage, ClassPathScanningCandidateComponentProvider scanner) {
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(basePackage);
        return beanDefinitions.stream()
                .map(BeanDefinition::getBeanClassName)
                .map(className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
    }

    private static ClassPathScanningCandidateComponentProvider getInstance(){
        if (scanner == null){
            scanner = new ClassPathScanningCandidateComponentProvider(false);
        }
        return scanner;
    }

}
