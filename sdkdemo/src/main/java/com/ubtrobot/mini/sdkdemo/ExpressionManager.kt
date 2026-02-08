package com.ubtrobot.mini.sdkdemo

import com.ubtrobot.express.ExpressApi
import com.ubtrobot.express.listeners.AnimationListener
import com.ubtrobot.master.component.ResourcePolicy
import kotlin.random.Random

object ExpressionManager {
    private const val TAG = "ExpressionManager"
    private val basicExpression = listOf("w_basic_0001_1", "w_basic_0001_2", "w_basic_0001_3", "w_basic_0001_4"
            , "w_basic_0001_5", "w_basic_0001_6", "w_basic_0001_7", "w_basic_0001_8", "w_basic_0001_9", "w_basic_0001_10"
            , "w_basic_0002_1", "w_basic_0002_2", "w_basic_0002_3", "w_basic_0002_4"
            , "w_basic_0003_1", "w_basic_0004_1", "w_basic_0005_1", "w_basic_0006_1"
            , "w_basic_0007_1", "w_basic_0007_2")

    private val randomExpressions = ArrayList<String>()

    private var standupExpression: String? = null

    @Volatile
    private var isPlaying = false

    private fun executeRandomExpressions() {
        isPlaying = true
        if (randomExpressions.isNotEmpty()) {
            randomExpressions.clear()
        }
        randomExpressions.addAll(getRandomExpressions())

    }

    fun execute() {
        executeRandomExpressions()
        standupExpression = randomExpressions.removeAt(0)

        ExpressApi.get().doExpress(standupExpression, 1, ResourcePolicy.GiveUp, object : AnimationListener {
            override fun onAnimationStart() {}

            override fun onAnimationEnd(p0: Int) {
                standupExpression = null
                if (randomExpressions.isNotEmpty() && isPlaying) {
                    execute()
                    return
                }
                isPlaying = false

            }

            override fun onAnimationRepeat(p0: Int) {}

        })

    }

    fun stop() {
        isPlaying = false
        standupExpression?.let {
            if (it.isNotEmpty()) {
                ExpressApi.get().stopExpress(it)
            }
            standupExpression = null
        }
        randomExpressions.clear()
    }

    private fun getRandomExpressions(): ArrayList<String> {
        val randomExpressions = ArrayList<String>()
        val random = Random(System.currentTimeMillis())
        val randomIndex = random.nextInt(0, basicExpression.size)
        val randomCount = generateRandom()
        for (i in 0 until randomCount) {
            randomExpressions.add(basicExpression[randomIndex])
        }
        return randomExpressions
    }

    private fun generateRandom(): Int {
        val random = Random(System.currentTimeMillis())
        return random.nextInt(3, 11)
    }
}