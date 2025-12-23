/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.server.audio;

import android.util.Log;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection help class.
 */
/* package */ class ReflectionHelper {
    private static final String TAG = "AS." + ReflectionHelper.class.getSimpleName();

    private static Result sFailure = new Result(false, null);

   public static Method getMethod(Class cls, String methodName,
            Class... params) {
        Method retMethod = null;
        try {
            retMethod = cls.getDeclaredMethod(methodName, params);
            if (retMethod != null) {
                retMethod.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return retMethod;
    }

   public static Method getMethod(Object obj, String methodName,
            Class... params) {
        boolean success = false;
        Throwable t = null;
        Method retMethod = null;
        if (obj == null || methodName == null) {
            Log.e(TAG, "getMethod, failed  Obj="
                    + obj
                    +", methodName=" + methodName);
            return retMethod;
        }
        try {
            retMethod = obj.getClass().getDeclaredMethod(methodName, params);
            if (retMethod != null) {
                retMethod.setAccessible(true);
            }
            success = true;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (!success) {
            Log.e(TAG, "getMethod, failed  Obj="
                    + obj
                    +", methodName=" + methodName);
        }
        return retMethod;
    }

   public static Field getField(Object obj, String fieldName,
            Class... params) {
        boolean success = false;
        Throwable t = null;
        Field retField = null;
        if (obj == null || fieldName == null) {
            Log.e(TAG, "getField, failed  obj="
                    + obj
                    +", methodName=" + fieldName);
            return retField;
        }
        try {
            retField = obj.getClass().getDeclaredField(fieldName);
            if (retField != null) {
                retField.setAccessible(true);
            }
            success = true;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        if (!success) {
            Log.e(TAG, "getField, failed Obj="
                    + obj
                    +", fieldName=" + fieldName);
        }
        return retField;
    }

   public static int getFieldIntValue(Object obj, String fieldName,
            Class... params) {
        boolean success = false;
        Throwable t = null;
        int retValue = -1;
        Field retField = null;
        if (obj == null || fieldName == null) {
            Log.e(TAG, "getField, failed  obj="
                    + obj
                    +", methodName=" + fieldName);
            return retValue;
        }
        try {
            retField = obj.getClass().getDeclaredField(fieldName);

            if (retField != null) {
                retField.setAccessible(true);
                retValue = retField.getInt(obj);
            }
            success = true;
        } catch (NoSuchFieldException e) {
            t = e;
        } catch (IllegalArgumentException e) {
            t = e;
        } catch (IllegalAccessException e) {
            t = e;
        }
        if (!success) {
            Log.e(TAG, "getFieldValue, failed Obj="
                    + obj
                    +", fieldName=" + fieldName + t);
        }
        return retValue;
    }

   public static Object getFieldObject(Object obj, String fieldName,
            Class... params) {
        Field retField = null;
        Object retFieldObj = null;
        boolean success = false;
        Throwable t = null;
        if (obj == null || fieldName == null) {
            Log.e(TAG, "getField, failed  Obj="
                    + obj);
            return retFieldObj;
        }
        try {
            retField = obj.getClass().getDeclaredField(fieldName);
            if (retField != null) {
                retField.setAccessible(true);
            }
          retFieldObj = retField.get(obj);
          success = true;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        if (!success) {
            Log.e(TAG, "getFieldObject, failed Obj="
                    + obj
                    +"fieldName=" + fieldName);
        }
        return retFieldObj;
    }

    /**
     * Replace static field.
     *
     * @param className Class name.
     * @param fieldName Static field name.
     * @param newInstance Class instance.
     */
    public static void replaceStaticField(
            String className, String fieldName, Object newInstance) {
        boolean success = false;
        Throwable t = null;
        try {
            Class clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, newInstance);
            success = true;
        } catch (ClassNotFoundException e) {
            t = e;
        } catch (NoSuchFieldException e) {
            t = e;
        } catch (IllegalAccessException e) {
            t = e;
        }
        if (!success) {
            Log.e(TAG, "Failed to replace static field for className="
                    + className
                    +"fieldName=" + fieldName
                    +",newInstance=" + newInstance);
        }
    }

    public static Object callMethod(Method method, Object object,
            Object... params) {
        Object ret = null;
        boolean success = false;
        Throwable t = null;
        if (method == null || object == null) {
            Log.e(TAG, "getField, failed  method="
                    + method + "object=" + object);
            return ret;
        }
        if (method != null) {
            try {
                ret = method.invoke(object, params);
                success = true;
            } catch (IllegalArgumentException e) {
                t = e;
            } catch (IllegalAccessException e) {
                t = e;
            } catch (InvocationTargetException e) {
                t = e;
            }
            if (!success) {
                Log.d(TAG, "Failed to callMethod:" + method.getName()
                            + " ret=" + ret);
            }
        }
        return ret;
    }

    public static void callSetBoolean(Field fieldName, Object object,
            Boolean status) {
        Object ret = null;
        boolean success = false;
        Throwable t = null;
        if (fieldName == null || object == null) {
            Log.e(TAG, "callSetBoolean, failed  fieldName="
                    + fieldName + "object=" + object);
            return;
        }
        if (fieldName != null) {
            try {
                if (status) {
                    fieldName.setBoolean(object, new Boolean(true));
                } else {
                    fieldName.setBoolean(object, new Boolean(false));
                }
                success = true;
            } catch (IllegalArgumentException e) {
                t = e;
            } catch (IllegalAccessException e) {
                t = e;
            }
            if (!success) {
                Log.d(TAG, "Failed to callSetBoolean:" + fieldName);
            }
        }
        return;
    }

    /**
     * Invoke method
     *
     * @param classPackage Class package.
     * @param className Class name.
     * @param methodName Method name.
     * @param params The params of method.
     * @return The invoke result.
     */
    public static Result callMethod(
            String classPackage, String className, String methodName,
            Object... params) throws InvocationTargetException {
        Throwable t = null;
        try {
            Class clazz;
            if (classPackage != null) {
                PathClassLoader loader = new PathClassLoader(classPackage,
                        ReflectionHelper.class.getClassLoader());
                clazz = Class.forName(className, false, loader);
            } else {
                clazz = Class.forName(className);
            }
            Class[] paramTypes = new Class[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i].getClass();
            }
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            Object ret = method.invoke(null, params);
            return new Result(true, ret);
        } catch (ClassNotFoundException e) {
            t = e;
        } catch (InvocationTargetException e) {
            Log.e(TAG,"[callMethod]Exception occurred to the invoke of" +
                    " throw it className=" + className +
                     "methodName" + methodName);
            throw e;
        } catch (NoSuchMethodException e) {
            t = e;
        } catch (IllegalAccessException e) {
            t = e;
        }
        Log.e(TAG,"[callMethod]Failed to call className=" + className
              + ", methodName=" + methodName);
        return sFailure;
    }

    /**
     * Invoke static method.
     *
     * @param classPackage Class package.
     * @param className Class name.
     * @param methodName Method name.
     * @param params The params of method.
     * @return The invoke result.
     */
    public static Result callStaticMethod(
            String classPackage, String className, String methodName,
            Object... params) throws InvocationTargetException {
        return callMethod(classPackage, className, methodName, params);
    }

    /**
     * The Result of execute method.
     */
    public static class Result {
        public boolean mSuccess;
        public Object mReturn;
        public Result(boolean success, Object returnValue) {
            mSuccess = success;
            mReturn = returnValue;
        }
    }

    public static Field getNonPublicField(Class<?> cls, String fieldName) {
        Field field = null;
        try {
            field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return field;
    }

    public static int getDeclaredMethod(Class<?> cls, Object instance, String methodName) {
        try {
            Class<?> paraClass[] = {};
            Method method = cls.getDeclaredMethod(methodName, paraClass);
            method.setAccessible(true);
            Object noObject[] = {};
            Object value = method.invoke(instance, noObject);
            return Integer.valueOf(value.toString());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
