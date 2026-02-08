# SquareActionManager API 文档

## 概述
`SquareActionManager`是一个Kotlin对象类，用于执行两种格式的动作文件(.ubx和.xml)，并通过内置的SquareActionSkill来执行这些动作，解决动作被其他行为打断的问题。

## 方法

### `init(context: Context)`
- **功能**: 初始化SquareActionManager
- **参数**: 
  - `context`: Android上下文对象

### `callSquareAction(context: Context, resourceName: String, listener: DoSquareActionListener)`
- **功能**: 通过动作广场执行动作
- **参数**:
  - `context`: Android上下文对象
  - `resourceName`: 资源文件名(支持.xml和.ubx格式)
  - `listener`: 动作执行的回调监听器

### `stopSquareAction(listener: SquareOperationListener)`
- **功能**: 停止动作广场的动作执行
- **参数**:
  - `listener`: 操作的回调监听器

### `smallActionSwitch(enable:Boolean)`
- **功能**：设置闲置小动作开关
- **参数**：
  - `enable`: 闲置小动作开关状态

### `getSmallActionStatus()`
- **功能**：获取闲置小动作开关状态


## 接口

### `SquareOperationListener`
- `onOperationFailure(errorCode: Int, msg: String?)`: 操作失败回调
- `onOperationFinished()`: 操作完成回调

### `DoSquareActionListener`
- `onActionFailure(errorCode: Int, msg: String?)`: 动作执行失败回调
- `onActionFinished()`: 动作执行完成回调
- `onStartDownload()`: 开始下载回调
- `onFinishDownload()`: 下载完成回调
