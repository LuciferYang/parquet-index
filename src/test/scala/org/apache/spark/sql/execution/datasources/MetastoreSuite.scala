/*
 * Copyright 2016 Lightcopy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources

import java.io.IOException

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.internal.IndexConf

import com.github.lightcopy.testutil.{SparkLocal, UnitTestSuite}
import com.github.lightcopy.testutil.implicits._

// Provided shortcut to create metastore in tests
private[datasources] trait TestMetastore {
  /** Load metastore and set metastore location to provided path */
  def testMetastore(spark: SparkSession, location: Path): Metastore = {
    testMetastore(spark, location.toString)
  }

  /** Load metastore and set metastore location to provided path as string */
  def testMetastore(spark: SparkSession, location: String): Metastore = {
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, location)
    new Metastore(spark, conf)
  }

  /** Load metastore with set of provided options */
  def testMetastore(spark: SparkSession, options: Map[String, String]): Metastore = {
    val conf = IndexConf.newConf(spark)
    options.foreach { case (key, value) =>
      conf.setConfString(key, value)
    }
    new Metastore(spark, conf)
  }
}

class MetastoreSuite extends UnitTestSuite with SparkLocal with TestMetastore {
  override def beforeAll {
    startSparkSession()
  }

  override def afterAll {
    stopSparkSession()
  }

  test("create non-existent directory for metastore") {
    withTempDir { dir =>
      val metastore = testMetastore(spark, dir / "test_metastore")
      metastore.metastoreLocation.endsWith("test_metastore") should be (true)
      val status = fs.getFileStatus(new Path(metastore.metastoreLocation))
      status.isDirectory should be (true)
      status.getPermission should be (Metastore.METASTORE_PERMISSION)
    }
  }

  test("create metastore and verify cache") {
    withTempDir { dir =>
      val metastore = testMetastore(spark, dir / "test_metastore")
      assert(metastore.cache != null)
      metastore.cache.asMap.size should be (0)
    }
  }

  test("use existing directory for metastore") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.metastoreLocation should be ("file:" + dir.toString)
      val status = fs.getFileStatus(new Path(metastore.metastoreLocation))
      status.isDirectory should be (true)
      status.getPermission should be (Metastore.METASTORE_PERMISSION)
    }
  }

  test("fail if metastore is not a directory") {
    withTempDir { dir =>
      val path = dir / "file"
      touch(path)
      val conf = IndexConf.newConf(spark)
      conf.setConf(IndexConf.METASTORE_LOCATION, path.toString)
      val err = intercept[IllegalStateException] {
        new Metastore(spark, conf)
      }
      assert(err.getMessage.contains("Expected directory for metastore"))
    }
  }

  test("fail if metastore has insufficient permissions") {
    withTempDir(new FsPermission("444")) { dir =>
      val conf = IndexConf.newConf(spark)
      conf.setConf(IndexConf.METASTORE_LOCATION, dir.toString)
      val err = intercept[IllegalStateException] {
        new Metastore(spark, conf)
      }
      assert(err.getMessage.contains("Expected directory with rwxrw-rw-"))
      assert(err.getMessage.contains("r--r--r--"))
    }
  }

  test("use directory with richer permissions") {
    withTempDir(new FsPermission("777")) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.metastoreLocation should be ("file:" + dir.toString)
    }
  }

  test("getMetastorePath - empty path provided") {
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, "")
    val metastore = new Metastore(spark, conf) {
      override def resolveMetastore(rawPath: Option[String], conf: Configuration): Path = {
        new Path("dummy")
      }
    }
    metastore.getMetastorePath should be (None)
  }

  // test current working directory
  test("resolveMetastore - empty path provided") {
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, "")
    val metastore = new Metastore(spark, conf)
    val path = metastore.resolveMetastore(None, new Configuration(false))
    assert(path.toString.startsWith(metastore.fs.getWorkingDirectory.toString))
  }

  test("getMetastorePath - local file system path provided") {
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, "file:/tmp/metastore")
    val metastore = new Metastore(spark, conf) {
      override def resolveMetastore(rawPath: Option[String], conf: Configuration): Path = {
        new Path("dummy")
      }
    }
    metastore.getMetastorePath should be (Some("file:/tmp/metastore"))
  }

  test("getMetastorePath - hdfs file system path provided") {
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, "hdfs://sandbox:8020/tmp/metastore")
    val metastore = new Metastore(spark, conf) {
      override def resolveMetastore(rawPath: Option[String], conf: Configuration): Path = {
        new Path("dummy")
      }
    }
    metastore.getMetastorePath should be (Some("hdfs://sandbox:8020/tmp/metastore"))
  }

  test("getMetastorePath - spaces only") {
    // we do not validate input string
    val conf = IndexConf.newConf(spark)
    conf.setConf(IndexConf.METASTORE_LOCATION, "   ")
    val metastore = new Metastore(spark, conf) {
      override def resolveMetastore(rawPath: Option[String], conf: Configuration): Path = {
        new Path("dummy")
      }
    }
    metastore.getMetastorePath should be (Some("   "))
  }

  //////////////////////////////////////////////////////////////
  // == Create index directory ==
  //////////////////////////////////////////////////////////////

  test("create - non-existent path, Append mode") {
    // when append mode is applied for non-existent folder, we disable append mode and convert it
    // into ErrorIfExists behaviour
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val location = metastore.location("identifier", new Path("/tmp/table"))
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Append) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (false)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, location) should be (true)
    }
  }

  test("create - non-existent path, Overwrite mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val location = metastore.location("identifier", new Path("/tmp/table"))
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Overwrite) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (false)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, location) should be (true)
    }
  }

  test("create - non-existent path, ErrorIfExists mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val location = metastore.location("identifier", new Path("/tmp/table"))
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.ErrorIfExists) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (false)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, location) should be (true)
    }
  }

  test("create - non-existent path, Ignore mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val location = metastore.location("identifier", new Path("/tmp/table"))
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Ignore) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (false)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, location) should be (true)
    }
  }

  test("create - existing path, Append mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val path = metastore.location("identifier", new Path("/tmp/table"))
      // create directory and existing file
      touch(path / "metadata")
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Append) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (true)
          // original file should still exist in the folder
          metastore.fs.exists(path / "metadata") should be (true)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, path) should be (true)
    }
  }

  test("create - existing path, Overwrite mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val path = metastore.location("identifier", new Path("/tmp/table"))
      // create directory and existing file
      touch(path / "metadata")
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Overwrite) {
        case (status, isAppend) =>
          triggered = true
          status.isDirectory should be (true)
          status.getPermission should be (Metastore.METASTORE_PERMISSION)
          isAppend should be (false)
          // original file should be deleted
          metastore.fs.exists(path / "metadata") should be (false)
      }
      triggered should be (true)
      Metastore.checkSuccessFile(fs, path) should be (true)
    }
  }

  test("create - existing path, ErrorIfExists mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = metastore.location("identifier", new Path("/tmp/table"))
      mkdirs(path)
      val err = intercept[IOException] {
        metastore.create("identifier", new Path("/tmp/table"), SaveMode.ErrorIfExists) {
          case (status, isAppend) => // do nothing
        }
      }
      assert(err.getMessage.contains("already exists, mode=ErrorIfExists"))
    }
  }

  test("create - existing path, Ignore mode") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val path = metastore.location("identifier", new Path("/tmp/table"))
      mkdirs(path)
      metastore.create("identifier", new Path("/tmp/table"), SaveMode.Ignore) {
        case (status, isAppend) => triggered = true
      }
      // should not invoke closure
      triggered should be (false)
    }
  }

  test("create - delete directory when fails, mode is not Append") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var path: Path = null
      val err = intercept[IllegalStateException] {
        metastore.create("identifier", new Path("/tmp/table"), SaveMode.Overwrite) {
          case (status, isAppend) =>
            // path should exist before exception is thrown
            path = status.getPath
            metastore.fs.exists(path) should be (true)
            throw new IllegalStateException("Test failure")
        }
      }
      err.getMessage should be ("Test failure")
      metastore.fs.exists(path) should be (false)
    }
  }

  test("create - keep directory when fails, mode is Append") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      // create folder to trigger append mode
      mkdirs(metastore.location("identifier", new Path("/tmp/table")))
      var path: Path = null
      val err = intercept[IllegalStateException] {
        metastore.create("identifier", new Path("/tmp/table"), SaveMode.Append) {
          case (status, isAppend) =>
            // path should exist before exception is thrown
            path = status.getPath
            isAppend should be (true)
            metastore.fs.exists(path) should be (true)
            throw new IllegalStateException("Test failure")
        }
      }
      err.getMessage should be ("Test failure")
      // path should still exist after exception is thrown
      metastore.fs.exists(path) should be (true)
    }
  }

  test("create - invalidate cache before creating index") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = metastore.location("identifier", new Path("/tmp/table"))
      metastore.cache.put(path, new TestIndexCatalog())
      metastore.cache.asMap.size should be (1)

      metastore.create("identifier", new Path("/tmp/table"), SaveMode.ErrorIfExists) {
        case (status, isAppend) => // no-op
      }
      metastore.cache.asMap.size should be (0)
    }
  }

  //////////////////////////////////////////////////////////////
  // == Delete index directory ==
  //////////////////////////////////////////////////////////////

  test("delete - non-existent path") {
    // should ignore deletion
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      metastore.delete("identifier", new Path("/tmp/table")) {
        case status => triggered = true
      }
      // should not invoke closure
      triggered should be (false)
    }
  }

  test("delete - existing path") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      var triggered = false
      val path = metastore.location("identifier", new Path("/tmp/table"))
      mkdirs(path)
      metastore.delete("identifier", new Path("/tmp/table")) {
        case status => triggered = true
      }
      triggered should be (true)
      metastore.fs.exists(path) should be (false)
    }
  }

  test("delete - invalidate cache before deleting index") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = metastore.location("identifier", new Path("/tmp/table"))
      metastore.cache.put(path, new TestIndexCatalog())
      metastore.cache.asMap.size should be (1)

      mkdirs(path)
      metastore.delete("identifier", new Path("/tmp/table")) {
        case status => // no-op
      }
      metastore.fs.exists(path) should be (false)
      metastore.cache.asMap.size should be (0)
    }
  }

  // this situation should not happen with normal workflow, because all operations will be done
  // using metastore API
  test("delete - do not invalidate cache if index does not exist") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = metastore.location("identifier", new Path("/tmp/table"))
      metastore.cache.put(path, new TestIndexCatalog())
      metastore.delete("identifier", new Path("/tmp/table")) {
        case status => // no-op
      }
      // metastore cache should still contain above entry
      assert(metastore.cache.getIfPresent(path) != null)
    }
  }

  //////////////////////////////////////////////////////////////
  // == Load index directory ==
  //////////////////////////////////////////////////////////////

  test("load - non-existent directory") {
    // should throw IOException
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val err = intercept[IOException] {
        metastore.load("identifier", new Path("/tmp/table")) {
          case status => new TestIndexCatalog()
        }
      }
      assert(err.getMessage.contains("Index does not exist"))
    }
  }

  test("load - existing directory without SUCCESS file") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val location = metastore.location("identifier", new Path("/tmp/table"))
      mkdirs(location)
      val err = intercept[IOException] {
        metastore.load("identifier", new Path("/tmp/table")) {
          case status => new TestIndexCatalog()
        }
      }
      assert(err.getMessage.contains("Possibly corrupt index, could not find success mark"))
    }
  }

  test("load - existing directory") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val location = metastore.location("identifier", new Path("/tmp/table"))
      mkdirs(location)
      Metastore.markSuccess(fs, location)
      var triggered = false
      metastore.load("identifier", new Path("/tmp/table")) {
        case status =>
          triggered = true
          new TestIndexCatalog()
      }
      triggered should be (true)
      // check that cache also contains entry
      assert(metastore.cache.getIfPresent(location) != null)
    }
  }

  test("load - use cache instead of reading from disk") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = new Path("/tmp/table")
      val location = metastore.location("identifier", path)
      mkdirs(location)
      Metastore.markSuccess(fs, location)

      val catalog1 = metastore.load("identifier", path) {
        case status => new TestIndexCatalog()
      }
      val catalog2 = metastore.load("identifier", path) {
        case status =>
          throw new IllegalStateException("Expected to use cache, failed to load entry")
      }

      catalog2 should be (catalog1)
      metastore.cache.asMap.size should be (1)
    }
  }

  //////////////////////////////////////////////////////////////
  // == Exists index directory ==
  //////////////////////////////////////////////////////////////

  test("exists - index path does not exist") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = new Path("/tmp/table")
      metastore.exists("identifier", path) should be (false)
    }
  }

  test("exists - directory exists, but no SUCCESS file") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = new Path("/tmp/table")
      val location = metastore.location("identifier", path)
      mkdirs(location)
      metastore.exists("identifier", path) should be (false)
    }
  }

  test("exists - directory and SUCCESS file both exist") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val path = new Path("/tmp/table")
      val location = metastore.location("identifier", path)
      mkdirs(location)
      Metastore.markSuccess(fs, location)
      metastore.exists("identifier", path) should be (true)
    }
  }

  //////////////////////////////////////////////////////////////
  // == Support and utility methods ==
  //////////////////////////////////////////////////////////////

  test("location - local fully-qualified path") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.location("test", new Path("file:/tmp/path")) should be (
        new Path("file:" + dir.toString / "test" / "file" / "tmp" / "path")
      )
    }
  }

  test("location - hdfs fully-qualified path") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.location("test", new Path("hdfs://sandbox:8020/tmp/path")) should be (
        new Path("file:" + dir.toString / "test" / "hdfs" / "tmp" / "path")
      )
    }
  }

  test("location - non-qualified path part") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.location("test", new Path("tmp/something")) should be (
        new Path("file:" + dir.toString / "test" / "file" / "tmp" / "something")
      )
    }
  }

  test("location - empty path") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val err = intercept[IllegalArgumentException] {
        metastore.location("test", new Path(""))
      }
      assert(err.getMessage.contains("Can not create a Path from an empty string"))
    }
  }

  test("location - empty identifier") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      val err = intercept[IllegalArgumentException] {
        metastore.location("", new Path("/tmp/path"))
      }
      assert(err.getMessage.contains("Empty invalid identifier"))
    }
  }

  test("toString - report metastore path") {
    withTempDir(Metastore.METASTORE_PERMISSION) { dir =>
      val metastore = testMetastore(spark, dir)
      metastore.toString should be (s"Metastore[metastore=file:$dir]")
    }
  }

  test("validateSupportIdentifier - empty or invalid identifier") {
    var err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier(null)
    }
    assert(err.getMessage.contains("Empty invalid identifier"))

    err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("")
    }
    assert(err.getMessage.contains("Empty invalid identifier"))
  }

  test("validateSupportIdentifier - invalid set of characters") {
    // identifier contains spaces
    var err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("test ")
    }
    assert(err.getMessage.contains("Invalid character"))

    // all characters are invalid
    err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("#$%")
    }
    assert(err.getMessage.contains("Invalid character"))

    // identifier contains uppercase characters
    err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("Test")
    }
    assert(err.getMessage.contains("Invalid character"))

    // identifier contains underscore
    err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("test_123")
    }
    assert(err.getMessage.contains("Invalid character"))

    // identifier contains hyphen
    err = intercept[IllegalArgumentException] {
      Metastore.validateSupportIdentifier("test-123")
    }
    assert(err.getMessage.contains("Invalid character"))
  }

  test("validateSupportIdentifier - valid identifier") {
    Metastore.validateSupportIdentifier("test")
    Metastore.validateSupportIdentifier("test1239")
    Metastore.validateSupportIdentifier("012345689")
    Metastore.validateSupportIdentifier("0123test")
  }

  test("markSuccess/checkSuccessFile") {
    withTempDir { dir =>
      Metastore.checkSuccessFile(fs, dir) should be (false)
      Metastore.markSuccess(fs, dir)
      Metastore.checkSuccessFile(fs, dir) should be (true)
    }
  }
}
