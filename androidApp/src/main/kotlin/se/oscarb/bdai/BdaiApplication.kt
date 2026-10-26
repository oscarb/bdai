package se.oscarb.bdai

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import se.oscarb.bdai.di.dataAndAgentModule
import se.oscarb.bdai.notifications.notificationsPlatformModule
import se.oscarb.bdai.ui.uiModule
import se.oscarb.bdai.voice.voicePlatformModule

class BdaiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@BdaiApplication)
                modules(
                    dataAndAgentModule,
                    uiModule,
                    voicePlatformModule,
                    notificationsPlatformModule
                )
            }
        }
    }
}
