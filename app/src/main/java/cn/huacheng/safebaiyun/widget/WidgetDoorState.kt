package cn.huacheng.safebaiyun.widget

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Widget 状态存储 key
 *
 * 用于记录每个 widget 实例（通过 appWidgetId 区分）绑定的门禁 ID。
 * 添加 widget 到桌面时，配置 Activity 会写入该 key；
 * 之后每次渲染 widget，provideGlance 会读取该 key 决定显示/开哪个门禁。
 */
val doorIdKey = stringPreferencesKey("widget_door_id")
