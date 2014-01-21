/*
 * LDIF
 *
 * Copyright 2011-2014 Universität Mannheim, MediaEvent Services GmbH & Co. KG
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

package ldif.local.datasources.dump

import java.net.URL
import org.slf4j.LoggerFactory
import java.io._
import ldif.local.runtime.impl.FileQuadReader
import java.util.Properties
import ldif.util.{Consts, CommonUtils}
import org.apache.any23.Any23
import org.apache.any23.source.ByteArrayDocumentSource
import org.apache.any23.extractor._
import org.apache.any23.writer.NTriplesWriter
import org.apache.any23.plugin.officescraper.ExcelExtractor
import org.apache.any23.rdf.RDFUtils

/**
 * Streams data from a given file path or URL
 * Accepts N-TRIPLE, N3 and RDF/XML serializations, CSV format and gzip, bz2 and zip compression.
 **/

@throws(classOf[Exception])
object DumpLoader {
  private val log = LoggerFactory.getLogger(getClass.getName)

  /**
   * @return InputStream
   */
  def getStream(sourceLocation : String, parameters : Properties = new Properties) : InputStream = {
    if (sourceLocation == null) {
      throw new Exception("Invalid data location" )
    }

    var url:URL = null
    var file:File = null
    var lang:String = null

    try {
      url = new URL(sourceLocation)
    } catch {
      case e:Exception => {
        file = CommonUtils.getFileFromPath(sourceLocation)
        if(!file.exists()) {
          throw new Exception("Unable to load the dump. File not found: " + sourceLocation)
        }
      }
    }

    if (url != null && url.getProtocol.toLowerCase.equals("file")) {
      file = new File(url.getFile)
    }

    if (file != null) {
      lang = ContentTypes.getLangFromExtension(file.getName)
    } else if (url != null) {
      lang = ContentTypes.getLangFromExtension(url.getFile)
    }
    if (lang == null) {
      throw new Exception("Unable to determine language for "+ sourceLocation + " based on file extension")
    }

    if (file != null) {
      getFileStream(file, lang, parameters)
    } else if (url != null) {
      getUrlStream(url, lang, parameters)
    } else {
      throw new Exception("Protocol \"" + url.getProtocol + "\" is not supported.")
    }
  }

  def getFileStream(file : File, lang : String = ContentTypes.langNQuad, parameters : Properties = new Properties) : InputStream = {
    log.info("Loading from " + file.getCanonicalPath)
    var inputStream:InputStream = null
    try {
      inputStream = new DecompressingStream(file).getStream
    } catch {
      case e:FileNotFoundException => {
        throw new Exception(file.getCanonicalPath + " vanished: " + e.getMessage)
      }
    }

    inputStream = convertFormat(inputStream, lang, parameters)

    new BufferedInputStream(inputStream)
  }

  private def getUrlStream(url : URL, lang : String, parameters : Properties) = {

    log.info("Loading from " + url.toString)
    var inputStream:InputStream = null

    try  {
      inputStream = new DecompressingStream(url).getStream
    } catch {
      case e:Exception => {
        throw new Exception(url + " did not provide any data")
      }
    }

    inputStream = convertFormat(inputStream, lang, parameters)

    new BufferedInputStream(inputStream)
  }

  private def convertFormat(inputStream : InputStream, lang : String, parameters : Properties) = lang match {
    case ContentTypes.langNTriple => inputStream
    case ContentTypes.langNQuad => inputStream
    case _ => toNTriples(inputStream, lang, parameters)
  }

  // Convert input format to N-Triples
  private def toNTriples(inputStream : InputStream, lang : String,  parameters : Properties) =  {

    // Set extraction values according to the given parameters
    // - see http://any23.apache.org/configuration.html
    val extractionParameters : ExtractionParameters = ExtractionParameters.newDefault()
    extractionParameters.setFlag("any23.extraction.metadata.timesize", false)
    extractionParameters.setProperty("any23.extraction.csv.field", parameters.getProperty("csvFieldSeparator", Consts.DEFAULT_CSV_FIELD_SEPERATOR))
    var documentUri = Consts.LDIF_NS
    val jobId = parameters.getProperty("jobId", null)
    if (jobId != null) {
      documentUri += jobId + "/"
    }

    val out = new ByteArrayOutputStream()
    val handler = new NTriplesWriter(out)

    if (lang.equals(ContentTypes.langXLSX)){
      // Use office-scraper plugin to parse the XLSX dump
      val extractor : ExcelExtractor = new ExcelExtractor()
      val extractionContext = new ExtractionContext(
        extractor.getDescription.getExtractorName,
        RDFUtils.uri(Consts.LDIF_NS + jobId +".xlsx")) // It requires a '.xlsx' suffix
      val extractionResult : ExtractionResult = new ExtractionResultImpl(extractionContext, extractor, handler)
      extractor.run(extractionParameters, extractionContext, inputStream, extractionResult)
    } else {
      // Use any23 core to parse the given dump
      val runner = new Any23
      val source = new ByteArrayDocumentSource(inputStream, documentUri, ContentTypes.getContentTypeFromLang(lang))
      runner.extract(extractionParameters, source, handler)
    }

    handler.close()
    new ByteArrayInputStream(out.toByteArray)
  }


  def dumpIntoFileQuadQueue(sourceLocation: String): FileQuadReader = {
    val stream = getStream(sourceLocation)
    QuadFileLoader.loadQuadsIntoTempFileQuadQueue(stream)
  }
}
