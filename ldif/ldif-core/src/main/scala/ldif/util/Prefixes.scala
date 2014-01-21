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

package ldif.util

import xml.Node

/**
 * Holds namespace prefixes.
 */
class Prefixes(val prefixMap : Map[String, String])
{
  override def toString = "Prefixes(" + prefixMap.toString + ")"

  /**
   * Combines two prefix objects.
   */
  def ++(prefixes : Prefixes) =
  {
    new Prefixes(prefixMap ++ prefixes.prefixMap)
  }

  def resolve(qualifiedName : String) = qualifiedName.split(":", 2) match
  {
    case Array("http", rest) if rest.startsWith("//") => qualifiedName
    case Array(prefix, suffix) => prefixMap.get(prefix) match
    {
      case Some(resolvedPrefix) => resolvedPrefix + suffix
      case None => throw new IllegalArgumentException("Unknown prefix: " + prefix)
    }
    case _ => throw new IllegalArgumentException("No prefix found in " + qualifiedName)
  }

  def toXML =
  {
    <Prefixes>
    {
      for((key, value) <- prefixMap) yield
      {
        <Prefix id={key} namespace={value} />
      }
    }
    </Prefixes>
  }

  def toSparql =
  {
    var sparql = ""
    for ((key, value) <- prefixMap)
       {
         sparql += "PREFIX "+key+": <"+value +"> "
       }
    sparql
  }

}

object Prefixes
{
  val empty = new Prefixes(Map.empty)

  implicit def fromMap(map : Map[String, String]) = new Prefixes(map)

  implicit def toMap(prefixes : Prefixes) = prefixes.prefixMap

  def apply(map : Map[String, String]) = new Prefixes(map)

  def fromXML(xml : Node) =
  {
    new Prefixes((xml \ "Prefix").map(n => (n \ "@id" text, n \ "@namespace" text)).toMap)
  }

  val stdPrefixes = new Prefixes(Map(
    "xsd" -> Consts.XSD,
    "rdf" -> Consts.RDF_NS,
    "rdfs" -> Consts.RDFS_NS,
    "owl" -> Consts.OWL_NS,
    "ldif" -> Consts.LDIF_NS, //added here as it will be used by all applications. no point in making user define in xml file
    "sieve" -> Consts.sievePrefix //added here as it will be used by all applications. no point in making user define in xml file
  ))

  def resolveStandardQName(qName: String) = stdPrefixes.resolve(qName)
}