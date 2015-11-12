package com.master;

import com.client.ClientFS;
import com.client.FileHandle;
import com.client.RID;
import com.master.Node;
import com.client.ClientFS.FSReturnVals;

import java.util.ArrayList;
import java.util.Scanner;

public class Master {
	
	Tree namespace;
	
	public Master() {
		// initialize all data structure
		
		Directory rootdir = new Directory();
		rootdir.name = "";
		namespace = new Tree(rootdir);
		
	}
	
	public FSReturnVals CreateDir(String src, String dirname) {
		// get node
		Node newNode = GetNode(src);
		if (newNode == null) {
			return FSReturnVals.SrcDirNotExistent;
		}
		
		// check if directory already exists
		if (newNode.GetChild(dirname) != null) {
			return FSReturnVals.DirExists;
		}
		
		// add directory
		Directory newDirectory = new Directory();
		newDirectory.name = dirname;
		newNode.AddChild(newDirectory);
		
		return FSReturnVals.Success;
	}
	
	// src must have '/' at the end of each directory
	public FSReturnVals DeleteDir(String src, String dirname) {
		// retrieve directory
		Node byeNode = GetNode(src);
		if (byeNode == null) {
			return FSReturnVals.SrcDirNotExistent;
		}
		
		// check if directory exists
		if (byeNode.GetChild(dirname) == null) {
			return FSReturnVals.DirDoesNotExist;
		}
		
		// if directory is not empty, CANNOT DELETE!
		if (!byeNode.GetChild(dirname).GetChildren().isEmpty()) {
			return FSReturnVals.DirNotEmpty;
		}
		
		// delete current directory
		byeNode.RemoveChild(dirname);
		
		return FSReturnVals.Success;
	}
	
	// src is FULL PATH of directory
	public FSReturnVals RenameDir(String src, String NewName) {
		// check if both give the same number of levels and are all same up to the last one
		ArrayList<String> path = ParsePath(src);
		ArrayList<String> newpath = ParsePath(NewName);
		if (path.size() != newpath.size()) {
			return FSReturnVals.Fail;
		}
		for (int i=0; i < path.size()-1; i++) {
			if (!path.get(i).equals(newpath.get(i))) {
				return FSReturnVals.Fail;
			}
		}
		
		// get node
		Node reNode = GetNode(src);
		if (reNode == null) {
			return FSReturnVals.SrcDirNotExistent;
		}
		
		// rename
		reNode.SetName(newpath.get(newpath.size()-1));
		
		return FSReturnVals.Success;
	}
	
	public String[] ListDir(String tgt) {
		
		Node dirNode = GetNode(tgt);
		if (dirNode == null) {
			return null;
		}
		
		ArrayList<Node> allDescendants = dirNode.GetAllDescendants();
		
		String[] ls = new String[allDescendants.size()];
		for (int i=0; i < ls.length; i++) {
			ls[i] = allDescendants.get(i).GetFullPath();
		}
		
		return ls;
	}
	
	public FSReturnVals CreateFile(String tgtdir, String filename) {
		// get node
		Node newNode = GetNode(tgtdir);
		if (newNode == null) {
			return FSReturnVals.SrcDirNotExistent;
		}
		
		// check if directory already exists
		if (newNode.GetChild(filename) != null) {
			return FSReturnVals.FileExists;
		}
		
		// add directory
		File newFile = new File();
		newFile.name = filename;
		newNode.AddChild(newFile);
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals DeleteFile(String tgtdir, String filename) {
		// retrieve directory
		Node byeNode = GetNode(tgtdir);
		
		// if directory does not exist, return error
		if (byeNode == null) {
			return FSReturnVals.SrcDirNotExistent;
		}
		
		// if file does not exist, return error
		if (byeNode.GetChild(filename) == null) {
			return FSReturnVals.FileDoesNotExist;
		}
		
		// delete the file
		byeNode.RemoveChild(filename);
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals OpenFile(String FilePath, FileHandle ofh) {
		// if invalid filepath, return error
		Node tempNode = GetNode(FilePath);
		if (tempNode == null) {
			return FSReturnVals.FileDoesNotExist;
		}
		
		ofh.FilePath = FilePath;
		ofh.RIDs = null;
		ofh.ChunkIndex = -1;
		ofh.ChunkServerID = -1;
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals CloseFile(FileHandle ofh) {
		// if invalid filepath, return error
		Node tempNode = GetNode(ofh.FilePath);
		if (tempNode == null) {
			return FSReturnVals.BadHandle;
		}
		
		// set all parameters of FileHandle to null
		ofh.FilePath = null;
		ofh.RIDs = null;
		ofh.ChunkIndex = -1;
		ofh.ChunkServerID = -1;
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals AppendRecord(FileHandle ofh, RID RecordID, int size) {
		// cannot write a record that is bigger than chunk's size
		if (size > ClientFS.chunkserver1.ChunkSize) {
			System.out.println("Record is larger than chunk size. Cannot AppendRecord.");
			return FSReturnVals.Fail;
		}
		
		// if invalid filepath, return error
		Node tempNode = GetNode(ofh.FilePath);
		if (tempNode == null) {
			return FSReturnVals.FileDoesNotExist;
		}
		
		// get file metadata
		File fmd = (File) tempNode.GetData();
		
		// Case 1: File has never been written to, or is empty
		if (fmd.cs1info == null) {
			
			// instantiate
			fmd.cs1info = new ArrayList<ArrayList<RID>>();
			
			// create new chunk
			ArrayList<RID> chunkinfo = new ArrayList<RID>();
			
			// create new RID
			RID rid = new RID();
			rid.chunkhandle = ClientFS.chunkserver1.createChunk();
			rid.byteoffset = 0;
			rid.size = size;
			rid.index = 0;
			
			// update file metatdata
			chunkinfo.add(rid);
			fmd.cs1info.add(chunkinfo);
			
			// update FH and RID
			ofh.RIDs = chunkinfo;
			ofh.ChunkIndex = 0;
			ofh.ChunkServerID = 1; // to be changed later
			RecordID.chunkhandle = rid.chunkhandle;
			RecordID.byteoffset = rid.byteoffset;
			RecordID.size = rid.size;
			RecordID.index = rid.index;
			
		}
		
		// Case 2: File has been written to, or is not empty
		else {
			
			// find amount of space left
			int spaceleft = -1;
			ArrayList<RID> chunkinfo = fmd.cs1info.get(fmd.cs1info.size()-1);
			RID rid = chunkinfo.get(chunkinfo.size()-1);
			spaceleft = ClientFS.chunkserver1.ChunkSize - rid.byteoffset - rid.size;
			
			RecordID.index = rid.index + 1;
			
			// Case 2a: Chunk does not have enough room (must create new chunk)
			if (size > spaceleft) {
				
				// create new chunk
				chunkinfo = new ArrayList<RID>();
				
				// create new RID
				rid = new RID();
				rid.chunkhandle = ClientFS.chunkserver1.createChunk();
				rid.byteoffset = 0;
				rid.size = size;
				rid.index = 0;
				
				// update metadata
				chunkinfo.add(rid);
				fmd.cs1info.add(chunkinfo);
				
				// update ChunkIndex
				ofh.ChunkIndex++;
				ofh.RIDs = chunkinfo;
				ofh.ChunkServerID = 1; // to be changed later
				
				// update RID
				RecordID.chunkhandle = rid.chunkhandle;
				RecordID.byteoffset = 0;
				RecordID.size = rid.size;
				RecordID.index = rid.index;
				
			}
			// Case 2b: Chunk does have enough room
			else {
				// create new RID
				RID rid2 = new RID();
				rid2.chunkhandle = rid.chunkhandle;
				rid2.byteoffset = rid.byteoffset + rid.size;
				rid2.size = size;
				rid2.index = rid.index + 1;
				
				// update metadata
				chunkinfo.add(rid2);
				
				// update RID
				RecordID.chunkhandle = rid.chunkhandle;
				RecordID.byteoffset = rid.byteoffset + rid.size;
				RecordID.size = rid.size;
				RecordID.index = rid.index;
				
			}
			
		}
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals DeleteRecord(FileHandle ofh, RID RecordID) {
		return null;
	}
	
	public FSReturnVals ReadFirstRecord(FileHandle ofh, RID RecordID) {
		// if invalid filepath, return error
		Node tempNode = GetNode(ofh.FilePath);
		if (tempNode == null) {
			return FSReturnVals.FileDoesNotExist;
		}
		
		// get file metadata
		File fmd = (File) tempNode.GetData();
		
		// if file is empty, return error
		if (fmd.cs1info == null) {
			return FSReturnVals.RecDoesNotExist;
		}
		
		// get first record from first chunk
		ArrayList<RID> chunkinfo = fmd.cs1info.get(0);
		RID rid = chunkinfo.get(0);
		
		// update FH and RID
		ofh.RIDs = chunkinfo;
		ofh.ChunkIndex = 0;
		ofh.ChunkServerID = 1; // to be changed later
		RecordID.chunkhandle = rid.chunkhandle;
		RecordID.byteoffset = rid.byteoffset;
		RecordID.size = rid.size;
		RecordID.index = 0;
		

		return FSReturnVals.Success;
	}
	
	public FSReturnVals ReadLastRecord(FileHandle ofh, byte[] payload, RID RecordID) {
		return null;
	}
	
	public FSReturnVals ReadNextRecord(FileHandle ofh, RID pivot, RID RecordID) {// if invalid filepath, return error
		// if invalid filepath, return error
		Node tempNode = GetNode(ofh.FilePath);
		if (tempNode == null) {
			return FSReturnVals.FileDoesNotExist;
		}
		
		// get file metadata
		File fmd = (File) tempNode.GetData();
		
		// if file is empty, return error
		if (fmd.cs1info == null) {
			return FSReturnVals.RecDoesNotExist;
		}
		
		// get next record
		
		// Case 1: not last record in chunk
		if (pivot.index < ofh.RIDs.size()-1) {
			RecordID.chunkhandle = ofh.RIDs.get(pivot.index+1).chunkhandle;
			RecordID.byteoffset = ofh.RIDs.get(pivot.index+1).byteoffset;
			RecordID.size = ofh.RIDs.get(pivot.index+1).size;
			RecordID.index = ofh.RIDs.get(pivot.index+1).index;
		}
		// Case 2: last record in chunk
		else {
			// Case 2a: not last chunk in file
			if (ofh.ChunkIndex < fmd.cs1info.size()-1) {
				ofh.RIDs = fmd.cs1info.get(++ofh.ChunkIndex);
				RecordID.chunkhandle = ofh.RIDs.get(0).chunkhandle;
				RecordID.byteoffset = ofh.RIDs.get(0).byteoffset;
				RecordID.size = ofh.RIDs.get(0).size;
				RecordID.index = ofh.RIDs.get(0).index;
			}
			// Case 2b: last chunk in file
			else {
				RecordID = null;
				return FSReturnVals.FileDoesNotExist;
			}
		}
		
		return FSReturnVals.Success;
	}
	
	public FSReturnVals ReadPrevRecord(FileHandle ofh, RID pivot, byte[] payload, RID RecordID) {
		return null;
	}
	
	
	// open a shell to process terminal directory commands
	public void CommandLine() {
		Scanner scan = new Scanner(System.in);
		String input;
		String [] args;
		
		
		// supports the following commands:
		// mkdir <filename>
		// rmdir <filename>
		// mv <filename>
		// ls
		// quit
		
		while (true) {
			// input
			System.out.print(">>> ");
			input = scan.nextLine();
			
			args = input.split(" ");
			
			if (args[0].equals("ls")) {
				String[] dirs = ListDir("/");
				for (int i=0; i < dirs.length; i++) {
					System.out.println(dirs[i]);
				}
				
				System.out.println("Used ls command");
			}
			else if (args[0].equals("mkdir")) {
				CreateDir("/", args[1]);
				
				System.out.println("Used mkdir command with " + args[1] + " argument");
			}
			else if (args[0].equals("rmdir")) {
				System.out.println("Used rmdir command with " + args[1] + " argument");
			}
			else if (args[0].equals("mv")) {
				System.out.println("Used mv command with " + args[1] + " argument");
			}
			else if (args[0].equals("quit")) {
				System.out.println("Exiting");
				break;
			}
			else if (args[0].equals("parsepath")) {
				ArrayList<String> parsedpath = ParsePath(args[1]);
				for (int i=0; i < parsedpath.size(); i++) {
					System.out.println(parsedpath.get(i));
				}
			}
			else {
				System.out.println("Invalid command...");
			}
			
		}
	}
	
	// process client requests through socket programming
	public void ReadAndProcessRequests() {
		
		
		
	}
	
	/*
	 * 
	 * UTILITY FUNCTIONS
	 * 
	 */
	
	// given a path, returns a list of each directory
	// For example, path = "/Jerry/Documents/File1"
	// will return: { "", "Jerry", "Documents", "File1" }
	// For example, path = "/Jerry/Documents/Homework/"
	// will return: { "", "Jerry", "Documents", "Homework" }
	private ArrayList<String> ParsePath(String path) {
		ArrayList<String> directories = new ArrayList<String>();
		
		int leftbound = 0, rightbound = 0;
		for (int i=0; i < path.length(); i++) {
			if (path.charAt(i) == '/') {
				rightbound = i;
				directories.add(path.substring(leftbound, rightbound));
				leftbound = i+1;
			}
		}
		
		if (rightbound != path.length()-1) {
			directories.add(path.substring(leftbound));
		}
		
		
		return directories;
	}
	
	// returns a node
	// or null if that node doesn't exist
	private Node GetNode(String filepath) {
		Node currentNode = namespace.root;
		
		ArrayList<String> path = ParsePath(filepath);
		
		// if the first directory is not root, returns error
		if (!path.get(0).equals("")) {
			return null;
		}
		
		// find the proper parent node
		// if the path is invalid, returns error
		String nextdir;
		for (int i=1; i < path.size(); i++) {
			nextdir = path.get(i);
			currentNode = currentNode.GetChild(nextdir);
			if (currentNode == null) {
				return null;
			}
		}
		
		return currentNode;
	}
	
	public static void main(String [] args) {
		Master ms = new Master();
		ms.CommandLine();
	}
}
