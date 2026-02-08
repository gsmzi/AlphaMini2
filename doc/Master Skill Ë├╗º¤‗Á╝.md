

## 基础知识

### 介绍

在机器人 Android 系统中，Master 旨在解决如下问题：

* 根据不同的职责分类，将机器人程序组件化
* 为各种组件提供消息代理，达成组件间的通信
* 根据产品政策方针，调度组件间的协作

## SDK 集成

### 准备

1.app或者Module的build.gradle文件里

```groovy
implementation fileTree(dir: 'libs', include: ['opensdk-v1.0.0-SNAPSHOT.aar'])
```
### 声明权限

在 AndroidManifest.xml 中声明连接 Master 的权限

```xml
<manifest>
    <!-- 声明连接 Master 的权限，该权限的保护级别为：signature -->
    <uses-permission android:name="com.ubtrobot.master.permission.MASTER" />
</manifest>
```
### 初始化

```java
// 在 YourApplication.onCreate 中初始化
public class YourApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化
        Master.initialize(this);

        // 初始化后，可获取 Master 单例
        Master master = Master.get();
    }
}
```

### 实现 MasterSkill

#### AndroidManifest 声明 MasterSkill

在 AndroidManifest.xml 中声明 MasterSkill 组件


```xml
<manifest>
    <application>
        <!-- 配置 MasterSkill 实现的 Class -->
        <!-- ❤❤❤ 注：必须配置 android:exported="true" ❤❤❤ -->
        <service android:name=".YourMasterSkill" android:exported="true">
             <!-- 指定 YourMasterSkill 元数据描述文件路径 -->
             <!-- ❤❤❤ 注："master.skill" 表明当前组件是 MasterSkill  ❤❤❤ -->
            <meta-data android:name="master.skill" android:resource="@xml/skill_${your_skill_name}" />
        </service>
        ...
    </application>
</manifest>
```

在 xml/skill_```${your_skill_name}``` xml 文件中配置 YourMasterSkill 元数据

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 配置 MasterSkill 元数据信息 -->
<!-- name: 标识同一个 APK 内 Skill 的唯一性 -->
<!-- label: 可选的 Skill 标签，用于显示 Skill 简称 -->
<!-- icon: 可选的 Skill 图标，用于示意 Skill。未配置则采用默认图标 -->
<!-- description: 可选的 Skill 描述，描述 Skill 的详情-->
<skill
    name="your_skill_name"
    label="@string/your_skill_label"
    icon="@mipmap/skill_icon"
    description="@string/your_skill_description">

    <!-- 配置 YourMasterSkill 能够处理的调用（call） -->
    <!-- path: 标识调用路径，必须整个 APK 内部唯一，也应该避免与其他 APK 的 skill.call.path 相同 -->
    <!-- description: 可选的 call 描述 -->
    <call path="/your-skill-name/foo/bar" description="@string/skill_call_foo_bar_description">
      <!-- call 如果需要支持语音 Interactor，可配置语音意图 -->
      <!-- category="speech" 表示语音意图，目前只支持这一种 category -->
      <intent-filter category="speech">
            <!-- sentence 配置语音命令 -->
            <utterance sentence="@string/foo_bar_command" />
            <utterance sentence="@string/foo_bar_command1" />
            <utterance sentence="@string/foo_bar_command2" />
        </intent-filter>
    </call>

    <!-- 配置其他能够处理的调用（call） -->
    <call path="/your-skill-name/baz/qux" description="@string/skill_call_baz_qux_description" />
    ...
</skill>
```

#### 实现 MasterSkill Class

```java
public class YourMasterSkill extends MasterSkill {

    @Override
    public void onSkillCreate() {
        // 根据需要选择做一些初始化工作
    }

    @Override
    public void onSkillStart() {
        // Skill Start 生命周期回调
        // Start 之后 & Stop 之前，可接收、处理、应答来自 MasterInteractor 的调用
    }

    @Call(path = "/your-skill-name/foo/bar")
    public void onFooBar(Request request, Responder responder) {
        // 处理 "/your-skill-name/foo/bar" 调用
        // request.getParam() 获取参数内容，关于 param 详见“参数对象”章节
        // 处理完毕后，需要通过 responder 应答，关于 responder 详见“调用 -> 应答器”章节
    }

    @Override
    protected void onCall(Request request, Responder responder) {
        // 对于在 xml/skill_${your_skill_name} 中描述的 Call：
        // 如果没有对应 @Call 注解的方法接收调用，统一在此接收、处理和应答
        // ❤❤❤ 建议为每个 Call 编写独立的接收方法，并用 @Call 注解 ❤❤❤
    }

    @Override
    public void onSkillStop() {
        // Skill Stop 生命周期回调
        // 此后，将不会接收到来自 MasterInteractor 的调用
    }

    @Override
    public void onSkillDestroy() {
        // 根据需要选择做一些清理工作
    }
}
```

#### 设置 MasterSkill 内部状态

```java
public class YourMasterSkill extends MasterSkill {

    @Override
    public void onSkillStart() {
    }

    private void stateSampleCode() {
        setState(aStateStr); // 设置某个状态
        setState(STATE_DEFAULT); // 设置到默认状态
        String currentState = getState(); // 获取当前状态
    }

    @Override
    protected void onCall(Request request, Responder responder) {
    }

    @Override
    public void onSkillStop() {
    }
}
```

#### 外部调用上下文内的方法

```java
private void executeContextTaskSampleCode() {
    // 支持在外部调用 MasterSkill 或 MasterService 2 种上下文内部的方法

    // 在 YourMasterSkill 外部调用 YourMasterSkill 方法
    boolean executed = Master.get().execute(YourMasterSkill.class, new ContextRunnable<YourMasterSkill>() {
        @Override
        public void run(YourMasterSkill skill) {
            // 调用 YourMasterSkill 中的方法
        }
    });

    // 在 YourMasterSkill 外部调用 YourMasterSkill 方法
    executed = Master.get().execute(YourMasterService.class, new ContextRunnable<YourMasterService>() {
        @Override
        public void run(YourMasterService service) {
            // 调用 YourMasterService 中的方法
        }
    });

    // executed 表示是否执行。在超出上下文生命周期（未创建、已销毁）范围时，run 方法将不会执行，返回 false
    // ❤❤❤ run 方法执行的线程与调用线程一致，run 会执行的情况，run 执行完成后才会返回 true ❤❤❤
}
```


### 参数对象

#### Param 基础

```java
void paramBaseSampleCode() {
    Param param = event.getParam() | req.getParam() | res.getParam() | *Param.(create|from)
    if (!param.isEmpty()) { // 参数判空
        // 获取参数类型
        String type = param.getType();
        type == "ParcelableParam" | "ProtoParam" | "ProtoLiteParam" | "GsonParam"
        // *Param.TYPE 常量描述了对应的类型
    }
}
```

#### ParcelableParam

```java
// Master SDK 默认支持
void parcelableParamSampleCode() {
    // 创建空的 BundleParam
    BundleParam bundleParam = BundleParam.create();
    // 从某个 Bundle 创建 BundleParam
    BundleParam anotherBundleParam = BundleParam.create(aBundle);

    // 填充参数字段
    bundleParam.content().putXxx(key, value);
    ...

    Param param = event.getParam() | req.getParam() | res.getParam();
    try {
        // 从参数对象创建 BundleParam
        // 须满足 (!param.isEmpty() && "BundleParam".equals(param.getType())，
        // 否则抛出 InvalidBundleParamException 异常
        BundleParam paramImpl = BundleParam.from(param);

        // 提取参数字段
        AType paramField = paramImpl.content().getXxx(key);
        ...
    } catch (BundleParam.InvalidBundleParamException e) {
        // 处理非法参数异常
    }
}
```

#### ProtoParam

```java
// 需添加 Gradle 依赖，详见“准备”章节
void protoParamSampleCode() {
    // 从 ProtoBuf 的 Any 对象创建 ProtoParam
    ProtoParam protoParam = ProtoParam.pack(any);
    // 从 ProtoBuf 的 消息对象（? extends com.google.protobuf.Message）创建 ProtoParam
    ProtoParam anotherProtoParam = ProtoParam.pack(aMessage);

    // 填充参数字段
    // ProtoParam.pack 构造 ProtoParam 前，由自定义的 ProtoBuf Message 对象的方法填充

    Param param = event.getParam() | req.getParam() | res.getParam();
    try {
        // 从参数对象创建 ProtoParam
        // 须满足 (!param.isEmpty() && "ProtoParam".equals(param.getType())，
        // 否则抛出 InvalidProtoParamException 异常
        ProtoParam paramImpl = ProtoParam.from(param);

        // 获取包含的 Any 对象
        Any any = paramImpl.getAny();
        // 讲 ProtoBuf Message 对象解包出来
        // AMessage extends com.google.protobuf.Message
        AMessage aMessage = paramImpl.unpack(AMessage.class);
        ...
    } catch (ProtoParam.InvalidProtoParamException e) {
        // 处理非法参数异常
    }
}
```

####ProtoLiteParam

```java
// 需添加 Gradle 依赖，详见“准备”章节
void protoLiteParamSampleCode() {
    // 从 ProtoBufLite 的 消息对象（? extends com.google.protobuf.MessageLite）创建 ProtoLiteParam
    ProtoLiteParam protoLiteParam = ProtoLiteParam.pack(aMessage);

    // 填充参数字段
    // ProtoLiteParam.pack 构造 ProtoLiteParam 前，由自定义的 ProtoBufLite Message 对象的方法填充

    Param param = event.getParam() | req.getParam() | res.getParam();
    try {
        // 从参数对象创建 ProtoLiteParam
        // 须满足 (!param.isEmpty() && "ProtoLiteParam".equals(param.getType())，
        // 否则抛出 InvalidProtoLiteParamException 异常
        ProtoLiteParam paramImpl = ProtoLiteParam.from(param);

        // 获取 ProtoLiteParam 内部的字节数组，
        // 而后将字节数组反序列化为 ProtoBufLite 消息对象（? extends com.google.protobuf.MessageLite）
        byte[] bytes = protoLiteParam.getByteArray();
        ...
    } catch (ProtoLiteParam.InvalidProtoLiteParamException e) {
        // 处理非法参数异常
    }
}
```

#### GsonParam

```java
// 需添加 Gradle 依赖，详见“准备”章节
void gsonParamSampleCode() {
    // 从 JSON String 创建 GsonParam
    GsonParam gsonParam = GsonParam.pack(jsonString);
    // 从普通 Java 对象创建 GsonParam
    GsonParam anotherGsonParam = GsonParam.pack(pojo);

    Param param = event.getParam() | req.getParam() | res.getParam();
    try {
        // 从参数对象创建 GsonParam
        // 须满足 (!param.isEmpty() && "GsonParam".equals(param.getType())，
        // 否则抛出 InvalidGsonParamException 异常
        GsonParam paramImpl = GsonParam.from(param);

        // 提取参数 Java 对象
        AType obj = param.unpack(type);
        AType obj = param.unpack(clazz);
        ...
    } catch (GsonParam.InvalidGsonParamException e) {
        // 处理非法参数异常
    }
}
```