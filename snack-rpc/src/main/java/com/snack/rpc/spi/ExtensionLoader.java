package com.snack.rpc.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service Provider Interface loader for Snack RPC.
 * 
 * Loads implementations from META-INF/services/ interfaces.
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public class ExtensionLoader<T> {
    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);
    
    private static final String SERVICES_DIR = "META-INF/services/";
    
    private final Class<T> type;
    private final Map<String, T> cachedInstances = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private final Map<String, Integer> cachedPriorities = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    
    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }
    
    /**
     * Create an ExtensionLoader for the given type.
     */
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        return new ExtensionLoader<>(type);
    }
    
    /**
     * Get the default extension instance.
     * Returns the extension with highest priority, or null if none.
     */
    public T getDefaultExtension() {
        loadExtensionClasses();
        
        if (cachedInstances.isEmpty()) {
            return null;
        }
        
        // Find extension with highest priority
        String highestName = null;
        int highestPriority = Integer.MIN_VALUE;
        
        for (Map.Entry<String, Integer> entry : cachedPriorities.entrySet()) {
            if (entry.getValue() > highestPriority) {
                highestPriority = entry.getValue();
                highestName = entry.getKey();
            }
        }
        
        return highestName != null ? getExtension(highestName) : null;
    }
    
    /**
     * Get extension by name.
     */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name cannot be null or empty");
        }
        
        loadExtensionClasses();
        
        T instance = cachedInstances.get(name);
        if (instance != null) {
            return instance;
        }
        
        Class<?> clazz = cachedClasses.get(name);
        if (clazz == null) {
            throw new IllegalArgumentException("No such extension: " + name + " for type " + type.getName());
        }
        
        try {
            instance = type.cast(clazz.newInstance());
            cachedInstances.put(name, instance);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create extension instance: " + name, e);
        }
    }
    
    /**
     * Get all available extension names.
     */
    public Set<String> getExtensionNames() {
        loadExtensionClasses();
        return new HashSet<>(cachedClasses.keySet());
    }
    
    /**
     * Get all available extension instances.
     */
    public Map<String, T> getExtensions() {
        loadExtensionClasses();
        
        Map<String, T> result = new HashMap<>();
        for (String name : cachedClasses.keySet()) {
            result.put(name, getExtension(name));
        }
        return result;
    }
    
    /**
     * Get extension with configuration.
     */
    public T getExtension(String name, Properties config) {
        T instance = getExtension(name);
        
        // If the instance implements Configurable interface, initialize it
        if (instance instanceof SerializerSPI) {
            ((SerializerSPI) instance).initialize(config);
        } else if (instance instanceof LoadBalanceSPI) {
            ((LoadBalanceSPI) instance).initialize(config);
        } else if (instance instanceof RegistrySPI) {
            try {
                ((RegistrySPI) instance).initialize(config);
                ((RegistrySPI) instance).start();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize registry: " + name, e);
            }
        }
        
        return instance;
    }
    
    /**
     * Load all extension classes from classpath.
     */
    private synchronized void loadExtensionClasses() {
        if (loaded) {
            return;
        }
        
        String fileName = SERVICES_DIR + type.getName();
        try {
            Enumeration<URL> urls = getClassLoader().getResources(fileName);
            
            if (!urls.hasMoreElements()) {
                logger.debug("No extension configuration files found for {}", new Object[]{type.getName()});
                loaded = true;
                return;
            }
            
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                loadFromURL(url);
            }
            
            loaded = true;
            logger.info("Loaded {} extensions for {}: {}", 
                    cachedClasses.size(), type.getName(), cachedClasses.keySet());
        } catch (Exception e) {
            logger.error("Failed to load extension classes for " + type.getName(), e);
        }
    }
    
    private void loadFromURL(URL url) {
        try (InputStream in = url.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    Class<?> clazz = Class.forName(line, false, getClassLoader());
                    if (!type.isAssignableFrom(clazz)) {
                        logger.warn("Class {} does not implement {}", new Object[]{line, type.getName()});
                        continue;
                    }
                    
                    T instance = type.cast(clazz.newInstance());
                    String name = null;
                    int priority = 0;
                    
                    if (instance instanceof SerializerSPI) {
                        name = ((SerializerSPI) instance).getName();
                        priority = ((SerializerSPI) instance).getPriority();
                    } else if (instance instanceof LoadBalanceSPI) {
                        name = ((LoadBalanceSPI) instance).getName();
                        priority = ((LoadBalanceSPI) instance).getPriority();
                    } else if (instance instanceof RegistrySPI) {
                        name = ((RegistrySPI) instance).getName();
                        priority = ((RegistrySPI) instance).getPriority();
                    } else {
                        // For non-SPI types, use class simple name
                        name = clazz.getSimpleName().toLowerCase();
                    }
                    
                    if (name == null || name.isEmpty()) {
                        logger.warn("Extension class {} does not provide a name", new Object[]{line});
                        continue;
                    }
                    
                    if (cachedClasses.containsKey(name)) {
                        // If duplicate name, keep the one with higher priority
                        int existingPriority = cachedPriorities.getOrDefault(name, 0);
                        if (priority > existingPriority) {
                            logger.debug("Replacing extension {} with higher priority version", new Object[]{name});
                            cachedClasses.put(name, clazz);
                            cachedPriorities.put(name, priority);
                            cachedInstances.remove(name); // Remove old instance
                        }
                    } else {
                        cachedClasses.put(name, clazz);
                        cachedPriorities.put(name, priority);
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to load extension class: " + line, e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load extension configuration from " + url, e);
        }
    }
    
    private ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            return cl;
        }
        return ExtensionLoader.class.getClassLoader();
    }
}