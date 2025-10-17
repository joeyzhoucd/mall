package com.mall.common.utils;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * æŸ¥è¯¢ç‰¹å®šçš„ç±»ï¼Œè¯¥å·¥å…·ä¸‹çš„æ–¹æ³•åº”è¯¥åœ¨ç¨‹åºå¯åŠ¨å®Œæˆä¹‹åŽæ‰å¯ä»¥ä½¿ç”¨
 */
public class BeanFindUtils {

    private static ClassPathScanningCandidateComponentProvider scanner;

    /**
     * æŸ¥è¯¢æŒ‡å®šäº†ç‰¹å®šæ³¨è§£çš„ç±»
     * @param basePackage ç±»æ‰€å­˜æ”¾çš„åŒ…å
     * @param annotation ç‰¹å®šçš„æ³¨è§£
     * @return ç›®æ ‡ç±»
     */
    public static Set<Class<?>> findAnnotation(String basePackage, Class<? extends Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = getInstance();
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return getClasses(basePackage, scanner);
    }

    /**
     * æŸ¥è¯¢å®žçŽ°äº†ç‰¹å®šæŽ¥å£çš„ç±»
     * @param basePackage ç±»æ‰€å­˜æ”¾çš„åŒ…å
     * @param interfaceClass ç‰¹å®šçš„æŽ¥å£
     * @return ç›®æ ‡ç±»
     */
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
