
---

# ExpressApi 类文档

## 概述
`ExpressApi` 是用于控制机器人表情的 API。机器人的眼部是两个 LCD 屏，通过在屏上渲染眼睛动画，机器人可以显示预定义的表情。本文档简要说明这些接口及 SDK 集成。

---

## 方法列表

### 1. `getExpressList()`
**描述**: 获取机器人上支持的表情列表。  
**返回值**:  
- 返回 `Express.ExpressInfo` 的列表。如果无法读取，则返回空列表。

---

### 2. `getCustomizeExpressList()`
**描述**: 获取机器人上支持的自定义表情动画列表。  
**返回值**:  
- 返回 `Express.ExpressInfo` 的列表。如果无法读取，则返回空列表。

---

### 3. `doExpress(String name)`
**描述**: 做一个表情动作。  
**参数**:  
- `name`: 表情名称。

---

### 4. `doExpress(String name, int loopCount)`
**描述**: 做一个表情动作，并指定运行次数。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。

---

### 5. `doExpress(String name, int loopCount, ResourcePolicy policy)`
**描述**: 做一个表情动作，并指定运行次数和资源策略。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。

---

### 6. `doExpress(String name, int loopCount, boolean tweenable, ResourcePolicy policy)`
**描述**: 做一个表情动作，并指定运行次数、是否使能表情过渡和资源策略。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `tweenable`: 是否使能表情过渡。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。

---

### 7. `doExpress(String name, int loopCount, ResourcePolicy policy, AnimationListener listener)`
**描述**: 做表情动画，并监听表情动画状态。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 8. `doExpress(String name, int loopCount, boolean tweenable, ResourcePolicy policy, AnimationListener listener)`
**描述**: 做表情动画，并指定运行次数、是否使能表情过渡、资源策略和动画监听器。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `tweenable`: 是否使能表情过渡。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 9. `doCustomizeExpress(String name, ResourcePolicy policy, AnimationListener listener)`
**描述**: 显示一个内置到机器人 SD 卡中的自定义表情动画。  
**参数**:  
- `name`: 表情名称。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 10. `doCustomizeExpress(String name, int loopCount, ResourcePolicy policy, AnimationListener listener)`
**描述**: 显示一个内置到机器人 SD 卡中的自定义表情动画，并指定运行次数。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 11. `doCustomizeExpress(String name, int loopCount, boolean tweenable, ResourcePolicy policy, AnimationListener listener)`
**描述**: 显示一个内置到机器人 SD 卡中的自定义表情动画，并指定运行次数、是否使能表情过渡和资源策略。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `tweenable`: 是否使能表情过渡。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 12. `doClipExpress(String name, Express.ClipInfo clipInfo, int loopCount, ResourcePolicy policy, AnimationListener listener)`
**描述**: 显示一个内置到机器人 SD 卡中的自定义表情动画，并指定剪辑信息、运行次数、资源策略和动画监听器。  
**参数**:  
- `name`: 表情名称。
- `clipInfo`: 剪辑信息，使用 `Express.ClipInfo` 类。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 13. `doCustomizeExpress(String name, int loopCount, boolean tweenable, ResourcePolicy policy, Express.ClipInfo clipInfo, AnimationListener listener)`
**描述**: 显示一个内置到机器人 SD 卡中的自定义表情动画，并指定运行次数、是否使能表情过渡、资源策略、剪辑信息和动画监听器。  
**参数**:  
- `name`: 表情名称。
- `loopCount`: 运行次数，取值范围为 0 到 `Character.MAX_VALUE`。
- `tweenable`: 是否使能表情过渡。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `clipInfo`: 剪辑信息，使用 `Express.ClipInfo` 类。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 14. `setFrame(String name, int frame)`
**描述**: PC 仿真调用，显示表情的指定帧。  
**参数**:  
- `name`: 表情名称。
- `frame`: 帧号，取值范围为 0 及以上。

---

### 15. `setFrame(String name, int frame, boolean customize)`
**描述**: PC 仿真调用，显示表情的指定帧，并指定是否为自定义表情。  
**参数**:  
- `name`: 表情名称。
- `frame`: 帧号，取值范围为 0 及以上。
- `customize`: 是否为自定义表情。

---

### 16. `doExpressTween(AnimationListener listener)`
**描述**: 调用 tween 动画，恢复到正常状态。  
**参数**:  
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 17. `doExpressTween(ResourcePolicy policy, AnimationListener listener)`
**描述**: 主动调用 tween 动画，并指定资源策略。  
**参数**:  
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 18. `doProgressExpress(int status, int progress, boolean animated, ResourcePolicy policy, AnimationListener listener)`
**描述**: 圆形进度条动画表情。  
**参数**:  
- `status`: 0 表示非充电，1 表示充电。
- `progress`: 当前进度，取值范围为 0 到 100。
- `animated`: 是否动画。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 19. `doTextExpress(TextValue value, long duration, ResourcePolicy policy, AnimationListener listener)`
**描述**: 显示文本。  
**参数**:  
- `value`: 要绘制的文本信息，使用 `TextValue` 类。
- `duration`: 文本持续时间。
- `policy`: 资源策略，使用 `ResourcePolicy` 枚举。
- `listener`: 动画监听器，使用 `AnimationListener` 接口。

---

### 20. `stopExpress(String express)`
**描述**: 停止指定表情名的系统表情。如果该表情动画结束，则什么都不做。  
**参数**:  
- `express`: 表情名称。

---

### 21. `stopExpress()`
**描述**: 停止当前正在运行的表情。如果没有表情动画，则什么都不做。

---

### 22. `stopCustomizeExpress(String express)`
**描述**: 停止指定表情名的自定义表情。如果该表情动画结束，则什么都不做。  
**参数**:  
- `express`: 表情名称。

---

### 23. `setBrightness(int brightness)`
**描述**: 设置眼睛屏的亮度。  
**参数**:  
- `brightness`: 亮度值，取值范围为 0 到 100。

---

## 静态方法

### `get()`
**描述**: 获取 `ExpressApi` 的单例实例。  
**返回值**:  
- 返回 `ExpressApi` 的单例对象。

---

## 内部实现细节

### 私有方法

#### `getContext()`
**描述**: 获取当前应用的上下文。  
**逻辑**:  
- 如果上下文为空，则通过反射调用 `ActivityThread.currentApplication()` 方法获取。

---

#### `createCompetitionSession()`
**描述**: 创建竞争会话，用于管理表情控制时的资源竞争。  
**逻辑**:  
- 返回包含指定服务的竞争会话。

---

## 注意事项
1. **资源管理**: 动作执行过程中会创建竞争会话，确保资源正确释放。
2. **回调接口**: 大部分方法需要传入回调接口以处理异步操作。
3. **异常处理**: 同步方法可能抛出异常，调用时需注意捕获。
4. **参数范围**: 确保传入的参数在指定范围内，避免无效操作。

---

以上是基于注释生成的 API 文档，涵盖了类的主要功能和方法的详细说明。