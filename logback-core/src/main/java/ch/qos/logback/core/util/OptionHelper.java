/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2011, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.util;

import java.lang.reflect.Constructor;
import java.util.Properties;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.PropertyContainer;

/**
 * @author Ceki Gulcu
 */
public class OptionHelper {

    /**
     * Creates an instance of the named class. If the context was configured with a named component
     * factory then we will get the component factory and use it to instantiate the object. If the
     * component factory cannot resolve to the class name then we will fall back to using the
     * default component factory, which uses the class loader of the context.
     * 
     * If the context was not configured with a component factory (the default) then we will just
     * use the default component factory.
     * 
     * @param className
     *            The class name of an object to instantiate.
     * @param superClass
     *            The instance we create must be assignable to this class.
     * @param context
     *            The logback context under which we will be creating the object.
     * @return
     * @throws IncompatibleClassException
     *             We could create an instance of the named class however it was not of a compatible
     *             type.
     * @throws DynamicClassLoadingException
     *             We ran into any number of problems resolving to the named class or instantiating
     *             an object of that class.
     */
    public static Object instantiateByClassName(String className, Class superClass, Context context)
            throws IncompatibleClassException, DynamicClassLoadingException {
        if (className == null) {
            throw new NullPointerException();
        }
        ComponentFactory factory = context.getComponentFactory();
        Object obj = null;
        boolean tryDefault = false;
        
        if (factory != null) {
            try {
                obj = factory.getInstance(className);

                if (obj == null) {
                    throw new NullPointerException();
                }
            } catch (ClassNotFoundException e) {
                tryDefault = true;
            }
        }

        if (factory == null || tryDefault) {
            ComponentFactory dcf = new ContextComponentFactory(context);
            try {
                obj = dcf.getInstance(className);
            } catch (ClassNotFoundException e) {
                throw new DynamicClassLoadingException("Failed to instantiate type " + className, e);
            }
        }

        if (!superClass.isAssignableFrom(obj.getClass())) {
            throw new IncompatibleClassException(superClass, obj.getClass());
        }

        return obj;
    }

  public static Object instantiateByClassNameAndParameter(String className,
                                              Class superClass, Context context, Class type, Object param) throws IncompatibleClassException,
          DynamicClassLoadingException {

    ClassLoader classLoader = Loader.getClassLoaderOfObject(context);
    return instantiateByClassNameAndParameter(className, superClass, classLoader, type, param);
  }

  @SuppressWarnings("unchecked")
  public static Object instantiateByClassName(String className,
                                              Class superClass, ClassLoader classLoader)
          throws IncompatibleClassException, DynamicClassLoadingException {
    return instantiateByClassNameAndParameter(className, superClass, classLoader, null, null);
  }

  public static Object instantiateByClassNameAndParameter(String className,
                                                          Class superClass, ClassLoader classLoader, Class type, Object parameter)
          throws IncompatibleClassException, DynamicClassLoadingException {

    if (className == null) {
      throw new NullPointerException();
    }
    try {
      Class classObj = null;
      classObj = classLoader.loadClass(className);
      if (!superClass.isAssignableFrom(classObj)) {
        throw new IncompatibleClassException(superClass, classObj);
      }
      if (type == null) {
        return classObj.newInstance();
      } else {
        Constructor constructor = classObj.getConstructor(type);
        return constructor.newInstance(parameter);
      }
    } catch (IncompatibleClassException ice) {
      throw ice;
    } catch (Throwable t) {
      throw new DynamicClassLoadingException("Failed to instantiate type "
              + className, t);
    }
  }

  /**
   * Find the value corresponding to <code>key</code> in <code>props</code>.
   * Then perform variable substitution on the found value.
   */
  // public static String findAndSubst(String key, Properties props) {
  // String value = props.getProperty(key);
  //
  // if (value == null) {
  // return null;
  // }
  //
  // try {
  // return substVars(value, props);
  // } catch (IllegalArgumentException e) {
  // return value;
  // }
  // }
  final static String DELIM_START = "${";
  final static char DELIM_STOP = '}';
  final static int DELIM_START_LEN = 2;
  final static int DELIM_STOP_LEN = 1;
  final static String _IS_UNDEFINED = "_IS_UNDEFINED";

  /**
   * @see #substVars(String, PropertyContainer, PropertyContainer)
   */
  public static String substVars(String val, PropertyContainer pc1) {
    return substVars(val, pc1, null);
  }

  /**
   * See  http://logback.qos.ch/manual/configuration.html#variableSubstitution
   */
  public static String substVars(String val, PropertyContainer pc1, PropertyContainer pc2) {

    StringBuffer sbuf = new StringBuffer();

    int i = 0;
    int j;
    int k;

    while (true) {
      j = val.indexOf(DELIM_START, i);

      if (j == -1) {
        // no more variables
        if (i == 0) { // this is a simple string

          return val;
        } else { // add the tail string which contains no variables and return
          // the result.
          sbuf.append(val.substring(i, val.length()));

          return sbuf.toString();
        }
      } else {
        sbuf.append(val.substring(i, j));
        k = val.indexOf(DELIM_STOP, j);

        if (k == -1) {
          throw new IllegalArgumentException('"' + val
                  + "\" has no closing brace. Opening brace at position " + j + '.');
        } else {
          j += DELIM_START_LEN;

          String rawKey = val.substring(j, k);

          // Massage the key to extract a default replacement if there is one
          String[] extracted = extractDefaultReplacement(rawKey);
          String key = extracted[0];
          String defaultReplacement = extracted[1]; // can be null

          String replacement = propertyLookup(key, pc1, pc2);

          // if replacement is still null, use the defaultReplacement which
          // can be null as well
          if (replacement == null) {
            replacement = defaultReplacement;
          }

          if (replacement != null) {
            // Do variable substitution on the replacement string
            // such that we can solve "Hello ${x2}" as "Hello p1"
            // where the properties are
            // x1=p1
            // x2=${x1}
            String recursiveReplacement = substVars(replacement, pc1, pc2);
            sbuf.append(recursiveReplacement);
          } else {
            // if we could not find a replacement, then signal the error
            sbuf.append(key + "_IS_UNDEFINED");
          }

          i = k + DELIM_STOP_LEN;
        }
      }
    }
  }

  public static String propertyLookup(String key, PropertyContainer pc1,
                                      PropertyContainer pc2) {
    String value = null;
    // first try the props passed as parameter
    value = pc1.getProperty(key);

    // then try  the pc2
    if (value == null && pc2 != null) {
      value = pc2.getProperty(key);
    }
    // then try in System properties
    if (value == null) {
      value = getSystemProperty(key, null);
    }
    if (value == null) {
      value = getEnv(key);
    }
    return value;
  }

  /**
   * Very similar to <code>System.getProperty</code> except that the
   * {@link SecurityException} is absorbed.
   *
   * @param key The key to search for.
   * @param def The default value to return.
   * @return the string value of the system property, or the default value if
   *         there is no property with that key.
   */
  public static String getSystemProperty(String key, String def) {
    try {
      return System.getProperty(key, def);
    } catch (SecurityException e) {
      return def;
    }
  }

  /**
   * Lookup a key from the environment.
   *
   * @param key
   * @return value corresponding to key from the OS environment
   */
  public static String getEnv(String key) {
    try {
      return System.getenv(key);
    } catch (SecurityException e) {
      return null;
    }
  }


  /**
   * Very similar to <code>System.getProperty</code> except that the
   * {@link SecurityException} is absorbed.
   *
   * @param key The key to search for.
   * @return the string value of the system property.
   */
  public static String getSystemProperty(String key) {
    try {
      return System.getProperty(key);
    } catch (SecurityException e) {
      return null;
    }
  }

  public static void setSystemProperties(ContextAware contextAware, Properties props) {
    for (Object o : props.keySet()) {
      String key = (String) o;
      String value = props.getProperty(key);
      setSystemProperty(contextAware, key, value);
    }
  }

  public static void setSystemProperty(ContextAware contextAware, String key, String value) {
    try {
      System.setProperty(key, value);
    } catch (SecurityException e) {
      contextAware.addError("Failed to set system property [" + key + "]", e);
    }
  }

  /**
   * Very similar to {@link System#getProperties()} except that the
   * {@link SecurityException} is absorbed.
   *
   * @return the system properties
   */
  public static Properties getSystemProperties() {
    try {
      return System.getProperties();
    } catch (SecurityException e) {
      return new Properties();
    }
  }

  static public String[] extractDefaultReplacement(String key) {
    String[] result = new String[2];
    result[0] = key;
    int d = key.indexOf(":-");
    if (d != -1) {
      result[0] = key.substring(0, d);
      result[1] = key.substring(d + 2);
    }
    return result;
  }

  /**
   * If <code>value</code> is "true", then <code>true</code> is returned. If
   * <code>value</code> is "false", then <code>true</code> is returned.
   * Otherwise, <code>default</code> is returned.
   * <p/>
   * <p> Case of value is unimportant.
   */
  public static boolean toBoolean(String value, boolean dEfault) {
    if (value == null) {
      return dEfault;
    }

    String trimmedVal = value.trim();

    if ("true".equalsIgnoreCase(trimmedVal)) {
      return true;
    }

    if ("false".equalsIgnoreCase(trimmedVal)) {
      return false;
    }

    return dEfault;
  }

  public static boolean isEmpty(String str) {
    return ((str == null) || CoreConstants.EMPTY_STRING.equals(str));
  }


}
