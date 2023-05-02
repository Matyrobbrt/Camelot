package uk.gemwire.camelot.script;

import com.google.common.base.Suppliers;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.ISnowflake;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ScriptObject implements ProxyObject {
    private final String name;
    private final Map<String, Object> values = new HashMap<>();

    public ScriptObject(String name) {
        this.name = name;
    }

    public static ScriptObject of(String name) {
        return new ScriptObject(name);
    }

    public static ScriptObject snowflake(String name, ISnowflake snowflake) {
        return new ScriptObject(name)
                .put("id", snowflake.getId());
    }

    public static ScriptObject mentionable(String name, IMentionable mentionable) {
        return snowflake(name, mentionable)
                .putMethod("asMention", args -> mentionable.getAsMention());
    }

    public ScriptObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public ScriptObject putMethod(String key, Executable method) {
        values.put(key, (ProxyExecutable) args -> method.invoke(createArgs(key, args)));
        return this;
    }

    public ScriptObject putVoidMethod(String key, ExecutableVoid method) {
        values.put(key, (ProxyExecutable) args -> {
            method.invoke(createArgs(key, args));
            return null;
        });
        return this;
    }

    private InvocationArguments createArgs(String methodName, Value[] args) {
        return new InvocationArguments() {
            @Override
            public Value[] getArguments() {
                return args;
            }

            @Override
            public RuntimeException createException(String message) {
                return new UnsupportedOperationException("Cannot invoke method '" + methodName + "': " + message);
            }
        };
    }

    public ScriptObject putLazyGetter(String key, Supplier<Object> method) {
        final Supplier<Object> sup = Suppliers.memoize(method::get);
        return put(key, (ProxyExecutable) args -> sup.get());
    }

    @Override
    public void putMember(String key, Value value) {
        values.put(key, value.isHostObject() ? value.asHostObject() : value);
    }

    @Override
    public boolean hasMember(String key) {
        return values.containsKey(key);
    }

    @Override
    public Object getMemberKeys() {
        return new ProxyArray() {
            private final Object[] keys = values.keySet().toArray();

            public void set(long index, Value value) {
                throw new UnsupportedOperationException();
            }

            public long getSize() {
                return keys.length;
            }

            public Object get(long index) {
                if (index < 0 || index > Integer.MAX_VALUE) {
                    throw new ArrayIndexOutOfBoundsException();
                }
                return keys[(int) index];
            }
        };
    }

    @Override
    public Object getMember(String key) {
        return values.get(key);
    }

    @Override
    public boolean removeMember(String key) {
        if (values.containsKey(key)) {
            values.remove(key);
            return true;
        } else {
            return false;
        }
    }

    public void transferTo(Value bindings) {
        this.values.forEach(bindings::putMember);
    }

    @Override
    public String toString() {
        return "ScriptObject{name=" + name + "}";
    }

    @FunctionalInterface
    public interface Executable {
        Object invoke(InvocationArguments arguments);
    }

    @FunctionalInterface
    public interface ExecutableVoid {
        void invoke(InvocationArguments arguments);
    }
}
