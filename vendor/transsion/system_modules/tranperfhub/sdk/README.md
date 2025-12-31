# TranPerfEvent SDK

## 集成方式

### Gradle
```groovy
repositories {
    maven { url 'https://your-maven-repo.com/releases' }
}

dependencies {
    compileOnly 'com.transsion.perfhub:tranperfevent:1.0.0'
}
```

### 使用示例
```java
import android.util.TranPerfEvent;
import vendor.transsion.hardware.perfhub.IEventListener;

// 注册监听器
IEventListener listener = new IEventListener.Stub() {
    @Override
    public void onEventStart(int eventId, long timestamp, int numParams,
                             int[] intParams, String extraStrings) {
        // 处理事件
    }
    
    @Override
    public void onEventEnd(int eventId, long timestamp, String extraStrings) {
        // 处理事件
    }
    
    @Override
    public int getInterfaceVersion() {
        return IEventListener.VERSION;
    }
    
    @Override
    public String getInterfaceHash() {
        return IEventListener.HASH;
    }
};

TranPerfEvent.registerEventListener(listener);
```

## 系统要求

- Android 5.0 (API 21) 或更高
- 需要运行在支持 TranPerfHub 的 Transsion 设备上
