package ldif.local.single

/*
 * LDIF
 *
 * Copyright 2011-2012 Freie Universität Berlin, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import runtime._
import ldif.entity.EntityDescription
import ldif.{EntityBuilderModule, EntityBuilderConfig}
import java.util.{Calendar, Properties}
import java.io._
import java.math.BigInteger
import org.slf4j.LoggerFactory
import ldif.util._
import ldif.modules.sieve.fusion.{FusionModule, EmptyFusionConfig, FusionConfig}
import ldif.modules.sieve.local.{SieveLocalQualityExecutor, SieveLocalFusionExecutor}
import ldif.runtime.QuadWriter
import ldif.output._
import ldif.modules.sieve.quality.{QualityTask, QualityConfig, QualityModule, EmptyQualityConfig}
import collection.mutable.HashMap
import ldif.config._
import ldif.local.runtime.{EntityWriter, EntityReader, QuadReader, ConfigParameters}
import ldif.local.runtime.impl._

class SieveJob (val config : IntegrationConfig, debugMode : Boolean = false) {

  private val log = LoggerFactory.getLogger(getClass.getName)

  // Object to store all kinds of configuration data
  private var configParameters: ConfigParameters = null
  private val stopWatch = new StopWatch

  private var lastUpdate : Calendar = null

  def runIntegration {

    if (config.sources == null || config.sources.size == 0)
      log.info("Sieve Job skipped - No data source files found")

    else
      synchronized {
        val sourceNumber = config.sources.size

        log.info("Sieve Job started")
        log.info("- Input < "+ sourceNumber +" source(s) found "+ config.sources.mkString(" "))
        log.info("- Output > "+ config.outputs.toString)
        log.info("- Properties ")
        for (key <- config.properties.keySet.toArray)
          log.info("  - "+key +" : " + config.properties.getProperty(key.toString) )

        stopWatch.getTimeSpanInSeconds

        // Validate configuration
        val fail = ConfigValidator.validateConfiguration(config)
        if(fail) {
          log.warn("Validation phase failed")
          sys.exit(1)
        } else {
          log.info("Validation phase succeeded in " + stopWatch.getTimeSpanInSeconds + "s")
        }

        // Quads that are not used in the integration flow, but should still be output
        val otherQuadsFile = File.createTempFile("ldif-other-quads", ".bin")
        // Quads that contain external sameAs links
        val sameAsQuadsFile = File.createTempFile("ldif-sameas-quads", ".bin")

        setupConfigParameters(otherQuadsFile, sameAsQuadsFile)

        // Load source data sets
        val quadReaders = loadDumps(config.sources)

      //Execute sieve (quality and fusion)
        val sieveInput: QuadReader = new MultiQuadReader(quadReaders.map{e => e}:_*)
        var sieveReader: QuadReader = executeSieve(config, Seq(sieveInput))

        lastUpdate = Calendar.getInstance

        //writeOutput(config, integratedReader)
        writeOutput(config, sieveReader)
      }
  }

  private def cloneQuadReaders(originalReaders: Seq[QuadReader]): Seq[QuadReader] = {
    originalReaders.map(qReader => qReader match {
      case cloneable: ClonableQuadReader => {
        cloneable.cloneReader
      }
      case _ => {
        log.error("Could not clone QuadReader. Results will not include triples from this reader.")
        new QuadQueue
      }
    })
  }

  // Setup config parameters
  def setupConfigParameters(outputFile: File, sameasFile: File) {
    outputFile.deleteOnExit()
    sameasFile.deleteOnExit()

    var otherQuads: QuadWriter = new FileQuadWriter(outputFile)
    var sameAsQuads: QuadWriter = new FileQuadWriter(sameasFile)

    configParameters = ConfigParameters(config.properties, otherQuads, sameAsQuads)

    // Setup LocalNode (to pool strings etc.)
    LocalNode.reconfigure(config.properties)
  }

  private def executeMappingPhase(config: IntegrationConfig, quadReaders: Seq[QuadReader], skip: Boolean): Option[Seq[QuadReader]] = {
    if(skip) {
      log.info("Skipping R2R phase.")
      return Some(quadReaders) // Skip R2R phase
    }
    else {
      var r2rReader = mapQuads(config, quadReaders)
      log.info("Time needed to map data: " + stopWatch.getTimeSpanInSeconds + "s")

      // Outputs intermadiate results
      Some(Seq(writeCopy(config, r2rReader.get.head, DT)))

    }
  }

  private def executeLinkingPhase(config: IntegrationConfig, quadReader: Seq[QuadReader], skip: Boolean): Seq[QuadReader] = {
    if(skip) {
      log.info("Skipping Silk phase.")
      return quadReader // Skip Silk phase
    }
    else {
      var linkReader = generateLinks(config.linkSpecDir, quadReader)
      log.info("Time needed to link data: " + stopWatch.getTimeSpanInSeconds + "s")
      log.info("Number of links generated by silk: " + linkReader.size)

      // Outputs intermadiate results
      linkReader = writeCopy(config, linkReader, IR)
      Seq(linkReader)
    }
  }

  private def executeSieve(config: IntegrationConfig, inputQuadsReaders : Seq[QuadReader]) : QuadReader = {

    val qualityModule = QualityModule.load(config.sieveSpecDir)
    val sieveQualityReader = qualityModule.config.qualityConfig match {
      case e: EmptyQualityConfig => {
        log.info("[QUALITY] No Sieve configuration found. No quality assessment will be performed.")
        new QuadQueue() // return empty queue
      }
      case c: QualityConfig => {
        executeQualityPhase(config, inputQuadsReaders, qualityModule)
      }
    }

    // now the scores from the quality assessment live in sieveQualityReader, and we need to get it into the fusion stuff
    val fusionInput : Seq[QuadReader] = cloneQuadReaders(inputQuadsReaders)
    val fusionModule = FusionModule.load(config.sieveSpecDir)
    val sieveFusionReader = fusionModule.config.fusionConfig match {
      case e: EmptyFusionConfig => {
        log.info("[FUSION] No Sieve configuration found. No fusion will be performed.")
        val echo = new QuadQueue()
        inputQuadsReaders.foreach(iqr => iqr.foreach(q => echo.write(q))); // copy input to output
        return echo;
      }
      case c: FusionConfig => {
        executeFusionPhase(config, fusionInput, qualityModule, fusionModule)
      }
    }
    // return both quality and fused quads
    new MultiQuadReader(sieveFusionReader, sieveQualityReader)
  }

  private def executeQualityPhase(config: IntegrationConfig, inputQuadsReader: Seq[QuadReader], qualityModule: QualityModule): QuadReader = {
    val sieveQualityReader = assessQuality(config.sieveSpecDir, inputQuadsReader, qualityModule)
    log.info("Time needed to assess data quality: " + stopWatch.getTimeSpanInSeconds + "s")
    log.info("Number of graphs quality-assessed by sieve: " + sieveQualityReader.size)
    sieveQualityReader
  }

  private def executeFusionPhase(config: IntegrationConfig, inputQuadsReader: Seq[QuadReader], qualityModule: QualityModule, fusionModule: FusionModule): QuadReader = {
    val sieveFusionReader = fuseQuads(config.sieveSpecDir, inputQuadsReader, qualityModule, fusionModule)
    log.info("Time needed to fuse data: " + stopWatch.getTimeSpanInSeconds + "s")
    log.info("Number of entities fused by sieve: " + sieveFusionReader.size)
    sieveFusionReader
  }

  private def executeURITranslation(inputQuadReader: QuadReader, linkReader: QuadReader, configProperties: Properties): QuadReader = {
    val integratedReader = URITranslator.translateQuads(inputQuadReader, linkReader, configProperties)

    log.info("Time needed to translate URIs: " + stopWatch.getTimeSpanInSeconds + "s")
    integratedReader
  }

  /**
   * Loads the dump files.
   */
  private def loadDumps(sources : Traversable[String]) : Seq[QuadReader] =
  {
    var quadQueues = Seq.empty[QuadReader]
    for (source <-  sources) {
      val sourceFile = new File(source)
      if(sourceFile.isDirectory) {
          for (dump <- sourceFile.listFiles)  {
           quadQueues = loadDump(dump) +: quadQueues
          }
      }
      else
         quadQueues = loadDump(sourceFile) +: quadQueues
    }
    quadQueues
  }

  /**
   * Loads a dump file.
   */
  private def loadDump(dump : File) : QuadReader = {
    // Integration component expects input data to be represented as Named Graphs, other formats are skipped
    //   if (ContentTypes.getLangFromExtension(dump.getName)!=ContentTypes.langNQuad) {
    //     log.warn("Input source skipped, format not supported: " + dump.getCanonicalPath)
    //     new QuadQueue
    //   }  else
    val quadQueue = new BlockingQuadQueue(Consts.DEFAULT_QUAD_QUEUE_CAPACITY)
    val discardFaultyQuads = config.properties.getProperty("discardFaultyQuads", "false").toLowerCase=="true"
    runInBackground
    {
      val inputStream = DumpLoader.getFileStream(dump)
      val bufferedReader = new BufferedReader(new InputStreamReader(inputStream))
      val quadParser = new QuadFileLoader(dump.getName, discardFaultyQuads)
      quadParser.readQuads(bufferedReader, quadQueue)
      quadQueue.finish
    }
    quadQueue
  }



  /**
   * Performs quality assessment
   */
  private def assessQuality(sieveSpecDir : File, inputQuadsReader : Seq[QuadReader], qualityModule: QualityModule) : QuadReader =
  {
    log.info("[QUALITY]")
    log.debug("Sieve will perform quality assessment, config=%s.".format(sieveSpecDir.getAbsolutePath))
    val qualityExecutor = new SieveLocalQualityExecutor

    // create a mapping between quality task and entity reader for corresponding entities
    var taskToReader = new HashMap[QualityTask, EntityReader] with scala.collection.mutable.Map[QualityTask, EntityReader]
    for ((task) <- qualityModule.tasks) {
      log.debug(task.qualitySpec.entityDescription.toString)
      //       val entityBuilderExecutor = getEntityBuilderExecutor(configParameters.copy(collectNotUsedQuads = true))    //andrea
      //       val readers = buildEntities(inputQuadsReader, entityDescriptions.toSeq, entityBuilderExecutor)             //andrea
      val readers : Seq[EntityReader] =  buildEntities(inputQuadsReader, Seq(task.qualitySpec.entityDescription), ConfigParameters(config.properties))
      if (readers.size > 0) {
        taskToReader += task -> readers.iterator.next
      }
      // TODO: cry if no reader could be created?
    }

    StringPool.reset
    log.info("Time needed to build entities for quality assessment phase: " + stopWatch.getTimeSpanInSeconds + "s")

    val output = new QuadQueue
    // for all tasks for all quads run scoring functions

    for ((task, reader) <- taskToReader) {
      qualityExecutor.execute(task, Seq(reader), output)
    }

    //new MultiQuadReader(output, entityBuilderExecutor.getNotUsedQuads) //andrea
    output
  }

  /**
   * Performs data fusion
   */
  private def fuseQuads(sieveSpecDir : File, inputQuadsReader : Seq[QuadReader], qualityModule: QualityModule, fusionModule: FusionModule) : QuadReader =
  {
    log.info("[FUSION]")
    log.debug("Sieve will perform fusion, config=%s.".format(sieveSpecDir.getAbsolutePath))

    // TODO: change so similar concept as shown above (why?)
    val fusionExecutor = new SieveLocalFusionExecutor()

    val entityDescriptions = fusionModule.tasks.toIndexedSeq.map(fusionExecutor.input).flatMap{ case StaticEntityFormat(ed) => ed }

    val entityReaders = buildEntities(inputQuadsReader, entityDescriptions, ConfigParameters(config.properties))

    StringPool.reset
    log.info("Time needed to build entities for fusion phase: " + stopWatch.getTimeSpanInSeconds + "s")

    val outputQueue = new QuadQueue

      //runInBackground
    {
      //for((sieveTask, readers) <- sieveModule.tasks.toList zip entityReaders.grouped(2).toList)
      for((sieveTask, reader) <- fusionModule.tasks.toList zip entityReaders.toList)
      {
        log.debug("sieveTask: %s; reader: %s.".format(sieveTask.name, reader.entityDescription))
        fusionExecutor.execute(sieveTask, Seq(reader), outputQueue)
      }
    }

    outputQueue

  }

  private def getEntityBuilderExecutor(configParameters: ConfigParameters) = {
      new EntityBuilderExecutor(configParameters)
  }

  /**
   * Build Entities.
   */
  private def buildEntities(readers : Seq[QuadReader], entityDescriptions : Seq[EntityDescription], entityBuilderExecutor : EntityBuilderExecutor) : Seq[EntityReader] =
  {
    var entityWriters: Seq[EntityWriter] = null
    val entityQueues = entityDescriptions.map(new EntityQueue(_, Consts.DEFAULT_ENTITY_QUEUE_CAPACITY))
    val fileEntityQueues = for(eD <- entityDescriptions) yield {
      val file = File.createTempFile("ldif_entities", ".dat")
      file.deleteOnExit
      new FileEntityWriter(eD, file, enableCompression = true)
    }

    val inmemory = config.properties.getProperty("entityBuilderType", "in-memory")=="in-memory"

    //Because of memory problems circumvent with FileQuadQueue */
    if(inmemory)
      entityWriters = entityQueues
    else
      entityWriters = fileEntityQueues

    try
    {
      val entityBuilderConfig = new EntityBuilderConfig(entityDescriptions.toIndexedSeq)
      val entityBuilderModule = new EntityBuilderModule(entityBuilderConfig)
      val entityBuilderTask = entityBuilderModule.tasks.head
      entityBuilderExecutor.execute(entityBuilderTask, readers, entityWriters)
    } catch {
      case e: Throwable => {
        e.printStackTrace
        sys.exit(2)
      }
    }

    if(inmemory)
      return entityQueues
    else
      return fileEntityQueues.map((entityWriter) => new FileEntityReader(entityWriter.entityDescription, entityWriter.inputFile, enableCompression = true ))
  }

  private def buildEntities(readers : Seq[QuadReader], entityDescriptions : Seq[EntityDescription], configParameters: ConfigParameters) : Seq[EntityReader] =
  {
    buildEntities(readers, entityDescriptions, new EntityBuilderExecutor(configParameters))
  }

  /**
   * Evaluates an expression in the background.
   */
  private def runInBackground(function : => Unit) {
    val thread = new Thread {
      private val listener: FatalErrorListener = FatalErrorListener

      override def run {
        try {
          function
        } catch {
          case e: Exception => listener.reportError(e)
        }
      }
    }
    thread.start
  }

  private def writeOutput(config : IntegrationConfig, reader : QuadReader) {
    for (writer <- config.outputs.getByPhase(COMPLETE)) {
      var count = 0

      //TODO add quadWriter string desc
      // log.info("Writing output to "+config.output.getCanonicalPath)
      if (writer != null) {
        while(reader.hasNext) {
          writer.write(reader.read())
          count += 1
        }
        writer.finish
      }

      log.info(count + " Quads written")
    }
  }

  // Writes the content of #reader to all output writers defined for #phase in #config
  private def writeCopy(config : IntegrationConfig, reader: QuadReader, phase : IntegrationPhase) : QuadReader = {
    val writers = config.outputs.getByPhase(phase)
    if(writers.size == 0 )
      reader
    else {
      val readerCopy = new QuadQueue
      while(reader.hasNext) {
        val next = reader.read
        for (writer <- writers)
          writer.write(next)
        readerCopy.write(next)
      }
      for (writer <- writers)
        writer.finish
      readerCopy
    }
  }

  private def writeDebugOutput(phase: String, outputFile: File, reader: QuadReader): QuadReader = {
    val newOutputFile = new File(outputFile.getAbsolutePath + "." + phase)
    copyAndDumpQuadQueue(reader, newOutputFile.getAbsolutePath)
  }

  def copyAndDumpQuadQueue(quadQueue: QuadReader, outputFile: String): QuadReader = {
    val quadOutput = File.createTempFile("ldif-debug-quads", ".bin")
    quadOutput.deleteOnExit
    val writer = new FileQuadWriter(quadOutput)
    val quadWriter = new BufferedWriter(new FileWriter(outputFile))

    while(quadQueue.hasNext) {
      val next = quadQueue.read
      quadWriter.write(next.toNQuadFormat)
      quadWriter.write(" .\n")
      writer.write(next)
    }
    quadWriter.flush()
    quadWriter.close()
    writer.finish
    return new FileQuadReader(writer.outputFile)
  }

  def getLastUpdate = lastUpdate
}


object SieveJob {
  LogUtil.init
  private val log = LoggerFactory.getLogger(getClass.getName)

  def main(args : Array[String])
  {
    if(args.length == 0) {
      log.warn("No configuration file given. \nUsage: SieveJob <integration job configuration file>")
      System.exit(1)
    }
    var debug = false
    val configFile = new File(args(args.length-1))

    if(args.length>=2 && args(0)=="--debug")
      debug = true

    var config : IntegrationConfig = null
    try {
      config = IntegrationConfig.load(configFile)
    }
    catch {
      case e:ValidationException => {
        log.error("Invalid Sieve Job configuration: "+e.toString +
          "\n- More details: http://www.assembla.com/code/ldif/git/nodes/ldif/ldif-core/src/main/resources/xsd/IntegrationJob.xsd")
        System.exit(1)
      }
    }

    val integrator = new SieveJob(config, debug)
    integrator.runIntegration
  }
}





