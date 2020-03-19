/*
 * Copyright 2014, Armenak Grigoryan, and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package com.strider.datadefender.requirement.functions;

import com.strider.datadefender.database.IDbFactory;
import com.strider.datadefender.requirement.Requirement;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Very basic registry for classes that need to be instantiated for use by
 * requirement functions.
 *
 * @author Zaahid Bateson <zaahid.bateson@ubc.ca>
 */
@RequiredArgsConstructor
@Log4j2
public class RequirementFunctionClassRegistry {

    private static RequirementFunctionClassRegistry instance = new RequirementFunctionClassRegistry();

    private Map<Class<?>, RequirementFunctionClass> singletons = new HashMap<>();

    public RequirementFunctionClass getFunctionsSingleton(Class<?> cls) {
        return singletons.get(cls);
    }

    public void register(Requirement requirements) throws
        NoSuchMethodException,
        InstantiationException,
        IllegalAccessException,
        IllegalArgumentException,
        InvocationTargetException {
        List<Method> fns = Stream.concat(
            requirements.getTables().stream()
                .flatMap((t) -> t.getColumns().stream())
                .flatMap((c) -> c.getFunctionList().getFunctions().stream()),
            requirements.getTables().stream()
                .flatMap((t) -> t.getColumns().stream())
                .map((c) -> c.getFunctionList().getCombiner())
        )
            .filter((fn) -> fn != null)     // combiner could be null
            .map((fn) -> fn.getFunction())
            .collect(Collectors.toList());

        log.debug("Found {} classes to register with functions for anonymization", fns.size());
        for (Method m : fns) {
            Class<?> cls = m.getDeclaringClass();
            log.debug(
                "Class: {}, is a RequirementFunctionClass: {}",
                () -> cls.getName(),
                () -> RequirementFunctionClass.class.isAssignableFrom(cls)
            );
            if (RequirementFunctionClass.class.isAssignableFrom(cls)) {
                log.debug("Registering RequirementFunctionClass: {}", cls);
                singletons.put(cls, (RequirementFunctionClass) cls.getDeclaredConstructor().newInstance());
            }
        }
    }

    public void initialize(IDbFactory factory) {
        for (Object o : singletons.values()) {
            if (o instanceof DatabaseAwareRequirementFunctionClass) {
                log.debug("Initializing DatabaseAwareRequirementFunctionClass for: {}", o.getClass());
                ((DatabaseAwareRequirementFunctionClass) o).initialize(factory);
            }
        }
    }

    public static RequirementFunctionClassRegistry singleton() {
        return instance;
    }
}
