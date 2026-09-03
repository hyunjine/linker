package com.hyunjine.linker.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

actual object LocalNotifications {
    actual fun schedule(id: String, title: String, body: String, epochSeconds: Long) {
        val nowSec = NSDate().timeIntervalSince1970
        if (epochSeconds.toDouble() <= nowSec) return

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitMinute
        val comps: NSDateComponents = NSCalendar.currentCalendar.components(units, date)
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)

        val center = UNUserNotificationCenter.currentNotificationCenter()
        // 같은 id 로 이미 등록돼 있으면 iOS 가 자동 덮어씀 (add 시 replace).
        center.addNotificationRequest(request, null)
    }

    actual fun cancel(id: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }

    actual fun cancelAll() {
        UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
    }
}
