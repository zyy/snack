package com.snack.rpc.serialization;

import com.snack.rpc.spi.SerializerSPI;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ProtoStuffSerializer implements SerializerSPI {
    private static Objenesis objenesis = new ObjenesisStd(true);

    public static final ProtoStuffSerializer serializer = new ProtoStuffSerializer();

    public ProtoStuffSerializer() {
    }

    /**
     * 序列化
     *
     * @param obj
     * @param <T>
     * @return
     */
    @Override
    public <T> byte[] serialize(T obj) {
        Class<T> cls = (Class<T>) obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            Schema<T> schema = RuntimeSchema.createFrom(cls);
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            buffer.clear();
        }
    }

    /**
     * 反序列化
     *
     * @param data
     * @param cls
     * @param <T>
     * @return
     */
    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) {
        try {
            T message = objenesis.newInstance(cls);
            Schema<T> schema = RuntimeSchema.createFrom(cls);
            ProtostuffIOUtil.mergeFrom(data, message, schema);
            return message;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    // ========== SerializerSPI implementation ==========
    
    @Override
    public String getName() {
        return "protostuff";
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority as default serializer
    }
    
    @Override
    public void initialize(java.util.Properties config) {
        // ProtoStuff doesn't need initialization
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        // ProtoStuff supports most Java objects via runtime schema
        return true;
    }
}
