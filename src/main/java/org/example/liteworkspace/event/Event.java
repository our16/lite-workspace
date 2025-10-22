package org.example.liteworkspace.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件基类
 * 
 * 所有事件都应该继承此类
 */
public abstract class Event {
    
    private final String eventId;
    private final LocalDateTime timestamp;
    private final String source;
    private final Map<String, Object> metadata;
    
    protected Event(String source) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.source = source;
        this.metadata = new ConcurrentHashMap<>();
    }
    
    protected Event(String source, Map<String, Object> metadata) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.source = source;
        this.metadata = new ConcurrentHashMap<>(metadata != null ? metadata : new ConcurrentHashMap<>());
    }
    
    /**
     * 获取事件ID
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * 获取事件时间戳
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取事件源
     */
    public String getSource() {
        return source;
    }
    
    /**
     * 获取事件元数据
     */
    public Map<String, Object> getMetadata() {
        return new ConcurrentHashMap<>(metadata);
    }
    
    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * 获取元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 获取事件类型名称
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 获取事件描述
     */
    public abstract String getDescription();
    
    /**
     * 是否为关键事件
     */
    public boolean isCritical() {
        return false;
    }
    
    @Override
    public String toString() {
        return String.format(
            "%s{id='%s', source='%s', timestamp='%s', metadata=%s}",
            getEventType(), eventId, source, timestamp, metadata.size()
        );
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Event event = (Event) obj;
        return eventId.equals(event.eventId);
    }
    
    @Override
    public int hashCode() {
        return eventId.hashCode();
    }
}
