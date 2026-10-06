/* ****************************************************************************
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

  See NOTICE file for details.
**************************************************************************** */
package org.jpype;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Java wants to make this action nearly impossible.
 *
 * Thus the have warnings against it that cannot be disabled. So we will skin
 * this cat another way.
 */
public class JPypeSignal
{

  static Thread main;

  static Object getSignalHandler(Class signalHandlerClazz, int signal) throws ClassNotFoundException
  {
    return Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]
    {
      signalHandlerClazz
    }, (proxy, method, args) ->
    {
      // Python can only be handed a signal that it has a handler for.  If its
      // handler is the default (always the case for SIGTERM in a script that
      // did not register one) the signal would be dropped.  Then do what the
      // JVM's own handler does: run the shutdown hooks and exit with status
      // 128 + signal.
      if (!pythonHandles(signal))
      {
        Runtime.getRuntime().exit(128 + signal);
        return null;
      }

      // Record the signal for Python before waking the main thread.  If the
      // main thread is blocked in Thread.sleep() or Object.wait(), interrupt()
      // makes it throw InterruptedException, and JPJavaError::toPython() then
      // looks for a pending signal.  In the other order the exception can be
      // converted before the signal is recorded, and Python sees
      // InterruptedException instead of KeyboardInterrupt.
      interruptPy(signal);
      main.interrupt();
      return null;
    });
  }

  static void installHandlers()
  {
    try
    {
      Class Signal = Class.forName("sun.misc.Signal");
      Class SignalHandler = Class.forName("sun.misc.SignalHandler");
      main = Thread.currentThread();
      Method method = Signal.getMethod("handle", Signal, SignalHandler);
      Object intr = Signal.getDeclaredConstructor(String.class).newInstance("INT");
      method.invoke(null, intr, getSignalHandler(SignalHandler, 2));
      Object intrTerm = Signal.getDeclaredConstructor(String.class).newInstance("TERM");
      method.invoke(null, intrTerm, getSignalHandler(SignalHandler, 15));
    } catch (InvocationTargetException | IllegalArgumentException | IllegalAccessException | InstantiationException | ClassNotFoundException | NoSuchMethodException | SecurityException ex)
    {
      // If we don't get the signal handler run without it.  (ANDROID)
    }
  }

  /** True if Python has a callable handler registered for the signal. */
  native static boolean pythonHandles(int signal);

  native static void interruptPy(int signal);

  native static void acknowledgePy();
}
