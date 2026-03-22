package com.snack.rpc.spi;

import com.snack.rpc.serialization.Serializer;

/**
 * SPI interface for serialization extensions.
 * 
 * Implementations should:
 * 1. Provide a public no-arg constructor
 * 2. Be registered in META-INF/services/com.snack.rpc.spi.SerializerSPI
 * 3. Implement the Serializer interface
 * 
 * Example implementation registration:
 *   META-INF/services/com.snack.rpc.spi.SerializerSPI
 *   com.example.MyCustomSerializer
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public interface SerializerSPI extends Serializer {
    
    /**
     * Returns the unique name of this serializer.
     * Used in configuration to select this serializer.
     * 
     * @return serializer name (e.g., "protostuff", "json", "hessian", "kryo")
     */
    String getName();
    
    /**
     * Returns the priority of this serializer (higher = more preferred).
     * Used when multiple serializers are available.
     * 
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Initialize the serializer with configuration.
     * 
     * @param config configuration properties
     */
    default void initialize(java.util.Properties config) {
        // default does nothing
    }
    
    /**
     * Whether this serializer supports the given class type.
     * 
     * @param clazz class to check
     * @return true if supported
     */
    default boolean supports(Class<?> clazz) {
        return true;
    }
}