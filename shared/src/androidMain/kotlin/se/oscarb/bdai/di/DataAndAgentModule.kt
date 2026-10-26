package se.oscarb.bdai.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import se.oscarb.bdai.agent.LiteRtLmEngine
import se.oscarb.bdai.agent.androidModelPaths
import se.oscarb.bdai.agent.data.ModelDownloader
import se.oscarb.bdai.agent.data.ModelManager
import se.oscarb.bdai.agent.domain.BirthdayAgent
import se.oscarb.bdai.agent.domain.BirthdayAgentService
import se.oscarb.bdai.agent.domain.BirthdayAgentServiceImpl
import se.oscarb.bdai.agent.domain.LlmEngine
import se.oscarb.bdai.agent.tools.provideAgentTools
import se.oscarb.bdai.data.BirthdayDatabase
import se.oscarb.bdai.data.DefaultBirthdayRepository
import se.oscarb.bdai.data.createBirthdayDatabase
import se.oscarb.bdai.domain.BirthdayRepository
import java.util.concurrent.TimeUnit

val dataAndAgentModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }
    single<BirthdayDatabase> { createBirthdayDatabase(androidContext()) }
    single<BirthdayRepository> {
        DefaultBirthdayRepository(
            database = get(),
            httpClient = get(),
            scope = get()
        )
    }
    single { androidModelPaths(androidContext()) }
    single<LlmEngine> { LiteRtLmEngine() }
    single { ModelDownloader(httpClient = get()) }
    single {
        ModelManager(
            engine = get(),
            downloader = get(),
            paths = get(),
            applicationScope = get()
        )
    }
    single {
        BirthdayAgent(
            engine = get(),
            tools = provideAgentTools(repository = get())
        )
    }
    single<BirthdayAgentService> {
        BirthdayAgentServiceImpl(
            agent = get(),
            modelManager = get()
        )
    }
}
