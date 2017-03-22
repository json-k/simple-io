![Simple IO](http://keeber.org/wp-content/uploads/2016/03/simple-io.png)

**Simple IO** is a library similar to the Apache Virtual Filesystem - wrapping different protocols in an abstraction that uses the pattern of files and folders.

It has a plugin architecture that currently supports the File, SMB, FTP and SFTP protocols.

# Philosophy

I built this library on three ideas:

1. **Efficiency** - if the implementation for the different protocols behaves the same way the code created is protocol independent. How many times can you write something that deletes a directory or implement a hot-folder?
2. **Simplicity** - both the API and the implementation. Not every possible scenario is accounted for, because until they are needed good enough is OK.
3. **Consistency** - why does a Java File created with a path containing a trailing slash not assume it is a directory when the file does not exist? Why does it return the path without the trailing slash when it does (exist)?

# Maven

The project is now available in the Maven Central Repository. For your Gradle build:

```
	compile 'org.keeber:simple-io:2.0.0'
```

#Quickstart
##Creation

It all starts with the static resolve method. Here we create a 'plain' file (in the user home): 

```java
File file=File.resolve("~/my/path/to/file.txt");
```

When the file is no longer needed it should be disposed - this releases any resources used by the file. The file should not be used after it is disposed.

```java
file.dispose();
```

Files can also be created from existing files (the reference to them - this is NOT akin to the touch command). This example creates a file called 'file.txt' in the provided folder:

```java
File dir=File.resolve("/user/local/");
File file=dir.create("file.txt");
```

## Plugins

The library includes a set of built-in plugins that can be loaded with the static addPlugin method. Files can then be created with one of the loaded protocols:

```java
File.addPlugin(FtpPlugin.create());
File.addPlugin(SmbPlugin.create());
File ftp=File.resolve("ftp://user:pass@server/path/to/file.txt");
```
 
Note: special characters in the authorization can (should) be URL escaped (eg: p@ss becomes p%40ss).

## Listing Folders

Listing files is controlled by two filter classes which are (functional) interfaces that determine which files should be listed and which directories should be followed (for easy recursion).

This example will list every text file (extension is 'txt') in every visible directory below the resolved folder:

```java
File folder = File.resolve("ftp://username:password@server/some/folder/");
List<File> files = folder.list(new GrabFilter() {

  @Override
  public boolean shouldGrab(File f) throws IOException {
    return f.getExtension().equals("txt");
  }
}, new MoveFilter() {

  @Override
  public boolean shouldMove(File f, int depth) throws IOException {
    return false;
  }
});
```

Because the filters (Grab and Move) are functional interfaces this can also be written:

```java
File folder = File.resolve("ftp://username:password@server/some/folder/");
List<File> files = folder.list((f) -> f.getExtension().equals("txt"), (f, d) -> false);
```


There are also some built in filters stored statically on the File.filters class and list operations can be sorted (by either providing a standard comparator or one generated by the built in factory methods):

```java
List<File> files = folder.list(File.filters.VISIBLE_FILES, File.filters.ONLY_THIS_DIRECTORY);
```
## Reading and Writing

Files can be read from and written to by opening them:

```java
OutputStream os=file.open(File.WRITE);
//...do something useful
os.close();
```

Don't forget to close the streams, because you always should, and this may be important to the particular file implementation (like finalizing an FTP transfer).

Copying the content from one file to another can be done quite simply using the copy method from the Streams class:

```java
File ifile = File.resolve("sftp://user:pass@server/path/to/file.txt");
File ofile = File.resolve("/user/local/file.txt");
 
Streams.copy(ifile.open(File.READ), ofile.open(File.WRITE), true);
```

## Operations

There are some useful operations attached to each file (part of the abstract file object and not the individual implementations):

```java
String content=file.operations.getStringContent();
 
ifile.operations.copyTo(ofile);
```

(Including a copy method, for when you don't need control over the resulting streams (eg: for counting)).

## Utilities

In addition to the resolve method the File class also provides other static utility methods:

```java
File desktop=File.getUserDesktop();

File temp=File.createTempFile("TempFile_", ".txt");
 ```
