package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.utils.TimeUtils;
import io.github.vizanarkonin.keres.core.utils.Tuple;

/*
 * Base class for user definition types.
 * Provides a full boilerplate with setup and teardown methods.
 * User will go through the following flow:
 * - setUp() method is executed once
 * - beforeTask() method is executed before every task method
 * - dynamic beforeTasks are executed before particular tasks (if before tasks had their names provided) or any task (if no task names were provided)
 * - task itself is executed. If there's only 1 task in the class - it will get picked everytime, if there are more than 1 - weight-based random is used
 * - dynamic afterTasks are executed after particular tasks (if before tasks had their names provided) or any task (if no task names were provided)
 * - afterTask() method is executed after every task
 * - tearDown() is executed once in the end of lifetime
 */
public abstract class KeresUserDefinition {
    private static final Logger                     log                 = LogManager.getLogger("KeresUserDefinition");
    private HashMap<String, ArrayList<Method>>      beforeTasks         = new HashMap<String, ArrayList<Method>>();
    private ArrayList<Tuple<KeresTask, Method>>          tasks               = new ArrayList<Tuple<KeresTask, Method>>();
    private HashMap<String, ArrayList<Method>>      afterTasks          = new HashMap<String, ArrayList<Method>>();
    private int                                     totalTasksWeight    = 0;

    public KeresUserDefinition() {
        for (Method method : this.getClass().getMethods()) {
            if (method.getAnnotation(KeresTask.class) != null) {
                KeresTask taskAnnotation = method.getAnnotation(KeresTask.class);
                tasks.add(new Tuple<KeresTask, Method>(taskAnnotation, method));
                totalTasksWeight += taskAnnotation.weight();
            } else if (method.getAnnotation(BeforeTask.class) != null) {
                String[] methodNames = method.getAnnotation(BeforeTask.class).taskNames();
                for (String methodName : methodNames) {
                    if (!beforeTasks.containsKey(methodName))
                        beforeTasks.put(methodName, new ArrayList<>());
                    
                    beforeTasks.get(methodName).add(method);
                }
            } else if (method.getAnnotation(AfterTask.class) != null) {
                String[] methodNames = method.getAnnotation(AfterTask.class).taskNames();
                for (String methodName : methodNames) {
                    if (!afterTasks.containsKey(methodName))
                        afterTasks.put(methodName, new ArrayList<>());
                    
                    afterTasks.get(methodName).add(method);
                }
            }
        }
    }

    public void setUp() {};
    public void beforeTask() {};
    public void afterTask() {};
    public void tearDown() {};

    public void task() {
        KeresTask metaData;
        Method task;

        if (tasks.size() == 0) {
            // Case 1 - no tasks in the list. Shouldn't happen, throw an exception
            throw new RuntimeException("Tasks list is empty. Can't proceed");
        } else if (tasks.size() == 1) {
            // Case 2 - one task in the list. Get it and execute
            metaData = tasks.get(0).getVal1();
            task = tasks.get(0).getVal2();
        } else {
            // Case 3 - more than one task in the list. Pick one at random with weight and execute it
            int index = 0;
            for (double r = Math.random() * totalTasksWeight; index < tasks.size() - 1; ++index) {
                r -= tasks.get(index).getVal1().weight();
                if (r <= 0.0) break;
            }

            metaData = tasks.get(index).getVal1();
            task = tasks.get(index).getVal2();
        }

        try {
            // first, we execute generic beforeTask
            beforeTask();

            // then we execute scenario-specific before tasks
            if (beforeTasks.containsKey(task.getName())) {
                beforeTasks.get(task.getName()).forEach(beforeTask -> {
                    try {
                        beforeTask.invoke(this);
                    } catch (InvocationTargetException ie) {
                        log.error(ie.getCause());
                        log.error(ExceptionUtils.getStackTrace(ie));
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                });
            }

            // then goes the task itself
            task.invoke(this);

            // after that we execute scenario-specific after tasks
            if (afterTasks.containsKey(task.getName())){
                afterTasks.get(task.getName()).forEach(afterTask -> {
                    try {
                        afterTask.invoke(this);
                    } catch (InvocationTargetException ie) {
                        log.error(ie.getCause());
                        log.error(ExceptionUtils.getStackTrace(ie));
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                });
            }

            // then we execute geenric afterTask
            afterTask();
            
            // And finally - we execute a delay, it one was provided
            if (metaData.delayAfterTaskInMs() > 0) {
                TimeUtils.waitFor(metaData.delayAfterTaskInMs());
            }
        } catch (InvocationTargetException ie) {
            log.error(ie.getCause());
            log.error(ExceptionUtils.getStackTrace(ie));
        } catch (Exception e) {
            log.error(e);
            log.error(ExceptionUtils.getStackTrace(e));
        }
    };
}
