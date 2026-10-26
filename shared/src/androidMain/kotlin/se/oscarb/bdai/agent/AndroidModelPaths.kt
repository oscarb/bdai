package se.oscarb.bdai.agent

import android.content.Context
import se.oscarb.bdai.agent.data.ModelPaths

/**
 * The model lives in the no-backup directory, since a 2.6 GB file would break Auto Backup.
 */
fun androidModelPaths(context: Context): ModelPaths = ModelPaths(
    modelsDir = context.noBackupFilesDir.resolve("models").absolutePath,
    cacheDir = context.cacheDir.resolve("litertlm").apply { mkdirs() }.absolutePath
)
