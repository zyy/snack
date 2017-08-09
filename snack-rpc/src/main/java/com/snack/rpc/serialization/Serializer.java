package com.snack.rpc.serialization;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public interface Serializer {

    <T> byte[] serialize(T obj);

    <T> T deserialize(byte[] data, Class<T> cls);
}
