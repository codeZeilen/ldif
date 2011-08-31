package ldif.local.scheduler

import ldif.local.datasources.dump.DumpLoader
import java.io.{FileWriter, InputStreamReader, BufferedReader}
import ldif.util.Identifier

class NQuadImportJob(val locationUrl : String, id : Identifier, refreshSchedule : String, dataSource : DataSource) extends ImportJob(id, refreshSchedule, dataSource) {

  override def load(writer : FileWriter) {

    // get bufferReader from Url
    val inputStream = new DumpLoader(locationUrl).getStream
    val bufferedReader = new BufferedReader(new InputStreamReader(inputStream))

    // parse and write to file
    //TODO
    //val quadParser = new QuadFileLoader("...")
    //quadParser.readQuads(bufferedReader, writer)
  }

}