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

package ldif.local

import ldif.local.runtime.{ConfigParameters}
import ldif.entity.EntityDescription
import tdb.TDBQuadStore
import java.io.File
import util.EntityBuilderReportPublisher
import ldif.runtime.QuadReader

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 07.07.11
 * Time: 17:21
 * To change this template use File | Settings | File Templates.
 */

object EntityBuilderFactory {
  def getEntityBuilder(configParameters: ConfigParameters, entityDescriptions: IndexedSeq[EntityDescription], reader : Seq[QuadReader], reporter : EntityBuilderReportPublisher) : EntityBuilderTrait = {
    val entityBuilderType = configParameters.configProperties.getProperty("entityBuilderType", "in-memory").toLowerCase
    entityBuilderType match {
      case "in-memory" => new EntityBuilder(entityDescriptions, reader, configParameters, reporter)
      case "quad-store" => {
        createQuadStoreEntityBuilder(configParameters, entityDescriptions, reader, reporter)
      }
    }
  }

  private def createQuadStore(quadStoreType: String, databaseLocation: String, reuseDatabase: Boolean): QuadStoreTrait = {
    quadStoreType match {
      case "tdb" => new TDBQuadStore(new File(databaseLocation), reuseDatabase)
      case _ => throw new RuntimeException("Unknown quad store type: " + quadStoreType)
    }
  }

  private def createQuadStoreEntityBuilder(configParameters: ConfigParameters, entityDescriptions: scala.Seq[EntityDescription], reader: scala.Seq[QuadReader],reporter : EntityBuilderReportPublisher) : EntityBuilderTrait = {
    val reuseDatabase = configParameters.configProperties.getProperty("reuseDatabase", "false").toLowerCase=="true"
    configParameters.configProperties.remove("reuseDatabase") // Only use for first phase
    val reuseDatabaseLocation = configParameters.configProperties.getProperty("reuseDatabaseLocation", System.getProperty("java.io.tmpdir"))
    val quadStoreType = configParameters.configProperties.getProperty("quadStoreType", "tdb").toLowerCase
    val databaseLocation = if(reuseDatabase)
        reuseDatabaseLocation
      else
        configParameters.configProperties.getProperty("databaseLocation", System.getProperty("java.io.tmpdir"))

    val quadStore = createQuadStore(quadStoreType, databaseLocation, reuseDatabase)
    new QuadStoreEntityBuilder(quadStore, entityDescriptions, reader, configParameters, reporter, reuseDatabase)
  }
}