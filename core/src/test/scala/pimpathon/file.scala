package pimpathon

import _root_.java.io.File
import org.junit.Test

import org.junit.Assert._
import pimpathon.any._
import pimpathon.file._


class FileTest {
  @Test def create {
    file.withTempDirectory(dir => {
      val child = file.file(dir, "child")
      assertFalse(child.exists)

      child.create()
      assertTrue(child.exists)
    })
  }

  @Test def cwd {
    // not a great test, but what to do other that use an alternate implementation ?
    assertEquals("pimpathon", file.cwd.getName)
  }

  @Test def named {
    file.withTempFile(tmp => {
      assertEquals("name", tmp.named("name").toString)
    })
  }

  @Test def changeToDirectory {
    file.withTempFile(file => {
      assertTrue(file.changeToDirectory().isDirectory)
    })
  }

  @Test def children {
    file.withTempDirectory(dir => {
      assertEquals(Set.empty[File], dir.children.toSet)

      val List(child, toddler) = file.files(dir, "child", "toddler").map(_.create()).toList
      assertEquals(Set(child, toddler), dir.children.map(_.named()).toSet)
    })

    assertEquals(Stream.empty[File], (null: File).children)
  }

  @Test def relativeTo {
    file.withTempDirectory(dir => {
      assertEquals("child", createFile(dir, "child").relativeTo(dir).getPath)

      assertEquals(dir.getName + "/kid",
        createFile(dir, "kid").relativeTo(dir.getParentFile).getPath)

      val parent = createDirectory(dir, "parent")

      assertEquals("parent", parent.relativeTo(dir).getPath)
      assertEquals("..", dir.relativeTo(parent).getPath)

      assertEquals("parent/child", createFile(parent, "child").relativeTo(dir).getPath)
    })
  }

  @Test def tree {
    assertEquals(Nil, new File("non-existent").tree)

    file.withTempFile(tmp =>      assertEquals(List(tmp), tmp.tree))
    file.withTempDirectory(tmp => assertEquals(List(tmp), tmp.tree))

    file.withTempDirectory(tmp => {
      val temp          = tmp.named()
      val child         = createFile(tmp, "child")
      val toddler       = createFile(tmp, "toddler")
      val teenageParent = createDirectory(tmp, "teenageParent")
      val brat          = createFile(teenageParent, "brat")

      assertEquals(Set(temp, child, toddler, teenageParent, brat), temp.tree.map(_.named()).toSet)
    })
  }

  @Test def withTempFile {
    assertFalse("Temp file should not exist after 'withTempFile'",
      file.withTempFile(tmp => {
        assertIsTemp(".tmp", "temp", expectedIsFile = true, tmp); tmp
      }).exists
    )

    assertFalse("Temp file should not exist after 'withTempFile'",
      file.withTempFile("suffix")(tmp => {
        assertIsTemp("suffix", "temp", expectedIsFile = true, tmp); tmp
      }).exists
    )

    assertFalse("Temp file should not exist after 'withTempFile'",
      file.withTempFile("suffix", "prefix")(tmp => {
        assertIsTemp("suffix", "prefix", expectedIsFile = true, tmp); tmp
      }).exists
    )
  }

  @Test def withTempDirectory {
    assertFalse("Temp directory should not exist after 'withTempDirectory'",
      file.withTempDirectory(tmp => {
        assertIsTemp(".tmp", "temp", expectedIsFile = false, tmp); tmp
      }).exists
    )

    assertFalse("Temp directory should not exist after 'withTempDirectory'",
      file.withTempDirectory("suffix")(tmp => {
        assertIsTemp("suffix", "temp", expectedIsFile = false, tmp); tmp
      }).exists
    )

    assertFalse("Temp directory should not exist after 'withTempDirectory'",
      file.withTempDirectory("suffix", "prefix")(tmp => {
        assertIsTemp("suffix", "prefix", expectedIsFile = false, tmp); tmp
      }).exists
    )
  }

  @Test def tempFile {
    val f = file.tempFile()
    assert(f.isFile())
    assert(f.exists())

    val suffix = ".sufferin"
    val prefix = "sucotash-"

    val f1 = file.tempFile(suffix)
    assert(f1.isFile())
    assert(f1.getName.endsWith(suffix))

    val f2 = file.tempFile(prefix = prefix)
    assert(f2.isFile())
    assert(f2.getName.startsWith(prefix))

    val f3 = file.tempFile(suffix, prefix)
    assert(f3.isFile())
    assert(f3.getName.startsWith(prefix))
    assert(f3.getName.endsWith(suffix))
  }

  @Test def tempDir {
    val f = file.tempDir()
    assert(f.isDirectory())
    assert(f.exists())

    val suffix = ".gosh"
    val prefix = "darnit-"

    val f1 = file.tempDir(suffix)
    assert(f1.isDirectory())
    assert(f1.getName.endsWith(suffix))

    val f2 = file.tempDir(prefix = prefix)
    assert(f2.isDirectory())
    assert(f2.getName.startsWith(prefix))

    val f3 = file.tempDir(suffix, prefix)
    assert(f3.isDirectory())
    assert(f3.getName.startsWith(prefix))
    assert(f3.getName.endsWith(suffix))
  }

  @Test def newFile {
    val dir = file.file("this directory does not exist")
    assertFalse(dir.exists)

    file.withTempDirectory(dir => {
      val child = file.file(dir, "and this file does not exist")
      assertFalse(child.exists)
      assertEquals(dir, child.getParentFile)

      val nested = file.file(dir, "parent/child")
      assertFalse(nested.exists)
      assertEquals(dir, nested.getParentFile.getParentFile)
    })
  }

  @Test def files {
    file.withTempDirectory(dir => {
      val List(child, nested) = file.files(dir, "child", "nested/child").toList

      assertEquals(file.file(dir, "child"), child)
      assertEquals(file.file(dir, "nested/child"), nested)
    })
  }

  private def assertIsTemp(
    expectedSuffix: String, expectedPrefix: String, expectedIsFile: Boolean, tmp: File) {

    assertTrue(s"Expected ${tmp.getName} to exist !", tmp.exists)

    assertTrue(s"Expected ${tmp.getName}, to begin with $expectedPrefix",
      tmp.getName.startsWith(expectedPrefix))

    assertTrue(s"Expected ${tmp.getName}, to end with $expectedSuffix",
      tmp.getName.endsWith(expectedSuffix))

    assertEquals(s"Expected ${tmp.getName} to be a " + (if (expectedIsFile) "file" else "directory"),
      expectedIsFile, tmp.isFile)
  }

  private def createDirectory(parent: File, name: String): File =
    createFile(parent, name).changeToDirectory()

  private def createFile(parent: File, name: String): File =
    new File(parent, name).named().create()
}
