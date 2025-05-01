package com.abmo

import com.abmo.common.Constants
import com.abmo.common.Constants.ABYSS_BASE_URL
import com.abmo.common.Logger
import com.abmo.model.Config
import com.abmo.services.ProviderDispatcher
import com.abmo.services.VideoDownloader
import com.abmo.util.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.system.exitProcess

class Application(private val args: Array<String>) : KoinComponent {

    private val videoDownloader: VideoDownloader by inject()
    private val providerDispatcher: ProviderDispatcher by inject()
    private val cliArguments: CliArguments by inject { parametersOf(args) }

    suspend fun run() {

        val outputFileName = cliArguments.getOutputFileName()
        val headers = cliArguments.getHeaders()
        val numberOfConnections = cliArguments.getParallelConnections()
        val videoIdsOrUrls = cliArguments.getVideoIdsOrUrlsWithResolutions()
        Constants.VERBOSE = cliArguments.isVerboseEnabled()

        if (outputFileName != null) {
            if (!isValidPath(outputFileName)) {
                exitProcess(0)
            }
        }

        videoIdsOrUrls.forEach { pairs ->
            val videoUrl = pairs.first
            val resolution = pairs.second

            val dispatcher = providerDispatcher.getProviderForUrl(videoUrl)
            val videoID = dispatcher.getVideoID(videoUrl)
            val defaultHeader = if (videoUrl.isValidUrl()) { mapOf("Referer" to videoUrl.extractReferer()) } else { emptyMap() }
            val url = "$ABYSS_BASE_URL/?v=$videoID"
            val videoMetadata = videoDownloader.getVideoMetaData(url, headers ?: defaultHeader)
            val videoSources = videoMetadata?.sources
                ?.sortedBy { it?.label?.filter { char -> char.isDigit() }?.toIntOrNull() }

            Logger.info("URL: '$url'")
            Logger.info(videoMetadata)
            
            if (videoSources == null) {
                Logger.error("Video with ID $videoID not found")
            } else {
                val mapResolution = when(resolution) {
                    "h" -> videoSources.maxBy { it?.size!! }?.label
                    "l" -> videoSources.minBy { it?.size!! }?.label
                    "m" -> videoSources.sortedBy { it?.size }.let { sorted ->
                            sorted.getOrNull((sorted.size - 1) / 2) }?.label
                    else -> videoSources.maxBy { it?.size!! }?.label
                }
                val defaultFileName = "${url.getParameter("v")}_${mapResolution}_${System.currentTimeMillis()}.mp4"
                val outputFile = outputFileName?.let { File(it) } ?: run {
                    Logger.warn("No output file specified. The video will be saved to the current directory as '$defaultFileName'.\n")
                    File(".", defaultFileName) // Default directory and name for saving video
                }
                if (mapResolution != null) {
                    val config = Config(url, mapResolution, outputFile, headers, numberOfConnections)
                    Logger.info("video with id $videoID and resolution $mapResolution being processed...\n")
                    try {
                        videoDownloader.downloadSegmentsInParallel(config, videoMetadata)
                    } catch (e: Exception) {
                        Logger.error(e.message.toString())
                    }
                }
            }
            if (videoIdsOrUrls.size > 1) {
                println("-----------------------------------------$videoID--------------------------------------------------------")
            }
        }




    }

}
