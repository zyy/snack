package com.snack.rpc.serialization;

import com.snack.rpc.RpcServer;
import com.snack.rpc.spi.ExtensionLoader;
import com.snack.rpc.spi.SerializerSPI;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages serializers loaded via SPI.
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public class SerializerManager {
    private static final Logger logger = LoggerFactory.getLogger(SerializerManager.class);
    
    private static volatile SerializerManager instance;
    
    private final ExtensionLoader<SerializerSPI> loader;
    private final Map<String, SerializerSPI> serializers = new ConcurrentHashMap<>();
    private SerializerSPI defaultSerializer;
    private volatile boolean initialized = false;
    
    private SerializerManager() {
        this.loader = ExtensionLoader.getExtensionLoader(SerializerSPI.class);
    }
    
    public static SerializerManager getInstance() {
        if (instance == null) {
            synchronized (SerializerManager.class) {
                if (instance == null) {
                    instance = new SerializerManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize serializers from configuration.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            Config config = RpcServer.getConfig();
            String defaultSerializerName = "protostuff"; // default
            
            if (config.hasPath("rpc.serialization")) {
                defaultSerializerName = config.getString("rpc.serialization");
            }
            
            // Load all available serializers
            Map<String, SerializerSPI> allSerializers = loader.getExtensions();
            serializers.putAll(allSerializers);
            
            // Configure each serializer
            for (SerializerSPI serializer : serializers.values()) {
                Properties props = extractSerializerConfig(config, serializer.getName());
                serializer.initialize(props);
            }
            
            // Set default serializer
            defaultSerializer = getSerializer(defaultSerializerName);
            if (defaultSerializer == null) {
                defaultSerializer = serializers.get("protostuff");
                if (defaultSerializer == null && !serializers.isEmpty()) {
                    defaultSerializer = serializers.values().iterator().next();
                }
            }
            
            initialized = true;
            
            logger.info("SerializerManager initialized with {} serializers, default={}",
                    serializers.size(), defaultSerializer != null ? defaultSerializer.getName() : "none");
            
        } catch (Exception e) {
            logger.error("Failed to initialize SerializerManager", e);
            throw new RuntimeException("Failed to initialize SerializerManager", e);
        }
    }
    
    /**
     * Get serializer by name.
     */
    public SerializerSPI getSerializer(String name) {
        if (!initialized) {
            initialize();
        }
        
        if (name == null || name.isEmpty()) {
            return getDefaultSerializer();
        }
        
        return serializers.get(name.toLowerCase());
    }
    
    /**
     * Get default serializer.
     */
    public SerializerSPI getDefaultSerializer() {
        if (!initialized) {
            initialize();
        }
        
        return defaultSerializer;
    }
    
    /**
     * Get all available serializers.
     */
    public Map<String, SerializerSPI> getAllSerializers() {
        if (!initialized) {
            initialize();
        }
        
        return new java.util.HashMap<>(serializers);
    }
    
    /**
     * Check if a serializer supports the given class.
     */
    public boolean supports(String serializerName, Class<?> clazz) {
        SerializerSPI serializer = getSerializer(serializerName);
        if (serializer == null) {
            return false;
        }
        
        return serializer.supports(clazz);
    }
    
    /**
     * Serialize object using default serializer.
     */
    public byte[] serialize(Object obj) {
        SerializerSPI serializer = getDefaultSerializer();
        if (serializer == null) {
            throw new IllegalStateException("No serializer available");
        }
        return serializer.serialize(obj);
    }
    
    /**
     * Deserialize object using default serializer.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        SerializerSPI serializer = getDefaultSerializer();
        if (serializer == null) {
            throw new IllegalStateException("No serializer available");
        }
        return serializer.deserialize(data, clazz);
    }
    
    /**
     * Serialize object using specified serializer.
     */
    public byte[] serialize(String serializerName, Object obj) {
        SerializerSPI serializer = getSerializer(serializerName);
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer not found: " + serializerName);
        }
        return serializer.serialize(obj);
    }
    
    /**
     * Deserialize object using specified serializer.
     */
    public <T> T deserialize(String serializerName, byte[] data, Class<T> clazz) {
        SerializerSPI serializer = getSerializer(serializerName);
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer not found: " + serializerName);
        }
        return serializer.deserialize(data, clazz);
    }
    
    private Properties extractSerializerConfig(Config config, String serializerName) {
        Properties props = new Properties();
        
        String configPath = "rpc.serialization." + serializerName;
        if (config.hasPath(configPath)) {
            Config serializerConfig = config.getConfig(configPath);
            for (Map.Entry<String, Object> entry : serializerConfig.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue().toString());
            }
        }
        
        return props;
    }
}