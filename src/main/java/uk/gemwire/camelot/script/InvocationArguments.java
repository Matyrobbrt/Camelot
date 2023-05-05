package uk.gemwire.camelot.script;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Interface used for easily accessing arguments passed into a {@linkplain org.graalvm.polyglot.proxy.ProxyExecutable}.
 *
 * @see ScriptObject#putMethod(String, ScriptObject.Executable)
 * @see ScriptObject#putVoidMethod(String, ScriptObject.ExecutableVoid)
 */
public interface InvocationArguments {
    /**
     * {@return the raw arguments}
     */
    Value[] getArguments();

    /**
     * {@return the argument at the given {@code index}, as a string}
     *
     * @param required if false, the lack of an argument at that index will return {@code null}
     */
    @Nullable
    @Contract("_, true -> !null")
    default String argString(int index, boolean required) {
        if (index >= getArguments().length) {
            if (required) {
                throw createException("Missing argument at position " + index);
            }
            return null;
        }
        return ScriptUtils.toString(getArguments()[index]);
    }

    /**
     * {@return the argument at the given {@code index}, as a {@link ScriptMap}}
     *
     * @param required if false, the lack of an argument at that index will return {@code null}
     */
    @Nullable
    @Contract("_, true -> !null")
    default ScriptMap argMap(int index, boolean required) {
        if (index >= getArguments().length) {
            if (required) {
                throw createException("Missing argument at position " + index);
            }
            return null;
        }
        return new ScriptMap(getArguments()[index]);
    }

    /**
     * Creates an exception to be thrown when the arguments are not valid.
     *
     * @param message the message of the exception
     * @return the exception
     */
    RuntimeException createException(String message);
}
