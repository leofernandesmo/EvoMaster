package org.evomaster.client.java.instrumentation.coverage.methodreplacement;

import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReplacementListTest {

    @Test
    public void testIntegerReplacement(){

        List<MethodReplacementClass> list = ReplacementList.getReplacements("java/lang/Integer");
        assertTrue(list.size() > 0);
    }

    @Test
    public void testReplacementMethods() {

        for (MethodReplacementClass mrc : ReplacementList.getList()) {

            //make sure that during testing all third-party libraries are available
            assertTrue(mrc.isAvailable(), "Not available: " + mrc.getClass().getName());

            for (Method m : mrc.getClass().getDeclaredMethods()) {

                Replacement r = m.getAnnotation(Replacement.class);
                if (r == null) {
                    continue;
                }

                assertTrue(!(r.replacingConstructor() && r.replacingStatic()), "We do not replace static constructors (would that even be possible?)");

                assertTrue(Modifier.isStatic(m.getModifiers()), "Issue with " + mrc.getClass() + ": Replacement methods must be static");

                if (r.type() == ReplacementType.BOOLEAN) {
                    assertSame(m.getReturnType(), Boolean.TYPE,
                            "Non-boolean return " + m.getReturnType() + " type for " +
                                    mrc.getClass().getName() + "#" + m.getName());
                }

                Class[] inputs = m.getParameterTypes();
                Class<?> targetClass = mrc.getTargetClass();
                assertNotNull(targetClass);

                if (r.type() != ReplacementType.TRACKER) {
                    assertTrue(inputs.length > 0, "Should always be at least 1 parameter, eg the idTemplate");
                    assertEquals(String.class, inputs[inputs.length - 1], "Last parameter should always be the idTemplate");
                }

                if (!r.replacingStatic() && !r.replacingConstructor()) {
                    //if not replacing a static method, then caller must be passed as first input
                    assertTrue(inputs.length >= 1);// caller

                    if (mrc instanceof ThirdPartyMethodReplacementClass) {
                        //must always be Object when dealing with third-party library replacements
                        assertEquals(Object.class, inputs[0]);
                    } else {
                        assertEquals(targetClass, inputs[0]);
                    }
                }

                if (r.replacingConstructor()) {
                    assertEquals(Void.TYPE, m.getReturnType());
                }

                int start = 0;
                if (!r.replacingStatic()) {

                    if (r.replacingConstructor()) {
                        start = 0; // no skips
                    } else {
                        start = 1; // skip caller
                    }
                }

                int end = inputs.length - 1;
                if (r.type() == ReplacementType.TRACKER) {
                    //no idTemplate at the end
                    end = inputs.length;
                }

                Class[] reducedInputs = Arrays.copyOfRange(inputs, start, end);

                if (!r.replacingConstructor()) {

                    Method targetMethod = null;
                    try {
                        targetMethod = targetClass.getMethod(m.getName(), reducedInputs);
                    } catch (NoSuchMethodException e) {
                        try {
                            targetMethod = targetClass.getDeclaredMethod(m.getName(), reducedInputs);
                        } catch (NoSuchMethodException noSuchMethodException) {
                            fail("No target method " + m.getName() + " in class " + targetClass.getName() + " with the right input parameters");
                        }
                    }
                    assertEquals(r.replacingStatic(), Modifier.isStatic(targetMethod.getModifiers()));

                } else{
                    Constructor targetConstructor = null;
                    try {
                        targetConstructor = targetClass.getConstructor(reducedInputs);
                    } catch (NoSuchMethodException e) {
                        fail("No constructor in class " + targetClass.getName() + " with the right input parameters");
                    }
                    assertNotNull(targetConstructor);

                    Optional<Method> orc = Arrays.stream(mrc.getClass().getDeclaredMethods())
                            .filter(it -> it.getName().equals(MethodReplacementClass.CONSUME_INSTANCE_METHOD_NAME))
                            .findFirst();
                    if(!orc.isPresent()){
                        fail("No instance consume method: "+MethodReplacementClass.CONSUME_INSTANCE_METHOD_NAME+"()");
                    }
                    Method rc = orc.get();
                    if (rc.getAnnotation(Replacement.class) != null) {
                        fail("Consume method should not be marked with replacement annotation");
                    }
                    assertEquals(0, rc.getParameterCount());
                    assertEquals(targetClass, rc.getReturnType());
                }
            }

        }

    }
}