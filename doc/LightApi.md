
---

# LightApi 类文档

## 概述
`LightApi` 是用于控制胸口灯和嘴巴灯的 API。胸口灯有八颗，ID 从 0 到 7，嘴巴灯有 1 颗，ID 为 0。胸口灯的颜色由红绿蓝三个颜色组成，每个颜色的取值范围是 0 到 255，其中 0 表示灭灯，255 表示最亮。胸口灯的颜色务必使用 `ColorUtil.rgbColor(int, int, int)` 方法来设置颜色。

---

## 常量

### 优先级常量
- `INFINITY`: -1，表示无限循环。
- `PRIORITY_L0`: 0，最低优先级。
- `PRIORITY_L1`: 1。
- `PRIORITY_L2`: 2。
- `PRIORITY_L3`: 3。
- `PRIORITY_L4`: 4，最高优先级。

---

## 静态方法

### `getInstance()`
**描述**: 获取 `LightApi` 的单例实例。  
**返回值**:  
- 返回 `LightApi` 的单例对象。

---

## 方法列表

### 1. `normalEffect(List<Integer> ids, int color, int duration, boolean append)`
**描述**: 创建持续一段时间的点亮灯效。  
**参数**:  
- `ids`: 要点亮的胸部灯光的 ID 列表。
- `color`: 灯光的颜色，使用 `ColorUtil.rgbColor(int, int, int)` 方法设置。
- `duration`: 灯光效果的持续时间（毫秒）。
- `append`: 是否将此效果添加到现有效果之后，`false` 时将覆盖之前的效果。

---

### 2. `cycleEffect(List<Integer> ids, int color, int onTime, int offTime, int duration, boolean append)`
**描述**: 启动亮-灭循环效果。  
**参数**:  
- `ids`: 灯的 ID 列表，指示哪些灯将参与此循环效果。
- `color`: 指定亮灯的颜色，使用 `ColorUtil.rgbColor(int, int, int)` 方法设置。
- `onTime`: 灯亮持续的时间（毫秒）。
- `offTime`: 灯灭持续的时间（毫秒）。
- `duration`: 整个循环效果的总持续时间（毫秒）。
- `append`: 是否将此效果添加到现有效果之后，`false` 时将覆盖之前的效果。

---

### 3. `breathEffect(List<Integer> ids, int color, int cycleTime, int duration, boolean append)`
**描述**: 创建呼吸灯效果。  
**参数**:  
- `ids`: LED 的标识列表，表示哪些 LED 将展示呼吸效果。
- `color`: 呼吸灯的颜色，使用 `ColorUtil.rgbColor(int, int, int)` 方法设置。
- `cycleTime`: 呼吸周期的时间，单位为毫秒，表示从最暗到最亮或从最亮到最暗所需的时间。
- `duration`: 效果持续的总时间，单位为毫秒，表示呼吸灯效果将持续显示的时间。
- `append`: 是否将此效果添加到现有效果之后，`false` 时将覆盖之前的效果。

---

### 4. `lightOffChest()`
**描述**: 关闭所有胸口灯灯效。

---

### 5. `lightOffChest(List<Integer> idList, ResourcePolicy policy, @Nullable ResponseListener<Void> listener)`
**描述**: 关闭胸部指定 ID 的灯光。  
**参数**:  
- `idList`: 灯的 ID 列表。
- `policy`: 资源使用策略。
- `listener`: 响应监听器，用于处理灯光关闭后的响应事件，可以为空。

---

### 6. `mouthOn(int color)`
**描述**: 嘴巴灯持续亮（直到主动调用熄灭）。  
**参数**:  
- `color`: 0-255，表示灯光颜色。

---

### 7. `mouthEffect(int color, int onTime, int offTime, int repeatTime)`
**描述**: 嘴巴灯效控制。  
**参数**:  
- `color`: 0-255，表示灯光颜色。
- `onTime`: 单次点亮时间（毫秒）。
- `offTime`: 单次熄灭时间（毫秒）。
- `repeatTime`: 重复次数，-1 表示无限循环。

---

### 8. `mouthOff()`
**描述**: 关闭嘴巴灯。

---

## 内部实现细节

### 私有方法

#### `lightUpChest(List<Integer> ids, int effect, int color, int onTime, int offTime, int duration, boolean append)`
**描述**: 实现不同的灯光效果。  
**逻辑**:  
- 创建竞争会话并设置资源策略。
- 调用 `/chestEffect` 接口设置灯光效果。
- 处理响应和异常。

---

#### `lightUp(List<LightWrapper.LightParam> params, ResourcePolicy policy, @Nullable ResponseListener<Void> listener, boolean isMouth)`
**描述**: 设置灯光亮起。  
**逻辑**:  
- 创建竞争会话并设置资源策略。
- 调用 `/setOn` 接口设置灯光亮起。
- 处理响应和异常。

---

#### `lightOff(List<Integer> params, ResourcePolicy policy, @Nullable ResponseListener<Void> listener, boolean isMouth)`
**描述**: 设置灯光熄灭。  
**逻辑**:  
- 创建竞争会话并设置资源策略。
- 调用 `/setOff` 接口设置灯光熄灭。
- 处理响应和异常。

---

#### `getCompetition(String service)`
**描述**: 创建竞争会话，用于管理灯光控制时的资源竞争。  
**逻辑**:  
- 返回包含指定服务的竞争会话。

---

## 注意事项
1. **颜色设置**: 必须使用 `ColorUtil.rgbColor(int, int, int)` 方法设置颜色。
2. **资源管理**: 动作执行过程中会创建竞争会话，确保资源正确释放。
3. **回调接口**: 大部分方法需要传入回调接口以处理异步操作。
4. **异常处理**: 同步方法可能抛出异常，调用时需注意捕获。