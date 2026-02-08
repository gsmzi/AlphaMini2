    
   # SpeechExt类文档

   ## 概述
    SpeechExt类提供了语音相关的接口，该类为单例，具体提供调用接口如下所示
    
   #### 1、设置VLM状态
```
     /**
     *@param enable true vlm 开启，false vlm 关闭
     */
    fun setVLMEnable(enable:Boolean) 
```


  #### 2、获取VLM状态
  `  fun getVLMEnable()`

  #### 3、注册接收进入故事模式事件

`    fun registerEnterStoryEvent(receiver: ContentReceiver)`


  #### 4、取消接收进入故事模式事件
     
 `   fun unregisterEnterStoryEvent()`

  #### 5、注册TTS接收者
`    fun registerTTSReceiver(receiver: ContentReceiver)`

  #### 6、 注册ASR接收者
 `    fun registerASRReceiver(receiver: ContentReceiver)`

  #### 7、取消注册TTS接收者
  `  fun unregisterTTSReceiver(receiver: ContentReceiver)`

  #### 8、取消注册ASR接收者
  `  fun unregisterASRReceiver(receiver: ContentReceiver)`

