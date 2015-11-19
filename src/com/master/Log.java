package com.master;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import com.master.Transaction.Command;

public class Log {
	//each log contains a list of transactions
	String filename; //file name of the log file
	File logFile;
	FileInputStream fis;
	FileOutputStream fos;
	HashMap<String, Transaction> transactions; //a list of transactions in this log file.
	
	public Log(String filename){
		transactions = new HashMap<String, Transaction>();
		this.filename = filename;
		this.logFile = new File(filename);
		
		try {
			if(!logFile.exists()){
				this.logFile.createNewFile();	
			}
			this.fis = new FileInputStream(logFile);
			this.fos = new FileOutputStream(logFile, false);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public boolean Commit(Transaction T){
		//generate a commit log record for the transaction in the log file
		//append the log form of that transaction.
		String msg = T.ID + ",Commit"; 
		AddMessage(msg);
		transactions.put(T.ID, T);
		//check size of the log file. If it reaches the size limit, make a checkpoint
		if(logFile.length() > (Master.LogFileSize-Master.TransactionMsgSize)){
			return false;
		}
		return true;
	}
	
	public void Start(Transaction T){
		//generate a start log 
		String msg = T.ID + ",Start"; 
		AddMessage(msg);
	}
	
	public void AddMessage(String msg){
		
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(fos));
		pw.println(msg);
		pw.flush();
		
	}
	
	//TODO: Parse log file and load transaction info when master starts.
	public void Load(){
		
		
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			String msg = null;
			String line = br.readLine();
			while(line != null){
				Transaction t = new Transaction();
				msg = line+"\n";
				line = br.readLine();
				if(line != null){
					msg += line + "\n";
				}
				line = br.readLine();
				if(line != null){
					msg += line;
				}
				line = br.readLine();
//				System.out.println(msg);
				if(t.toTransaction(msg)){
					this.transactions.put(t.ID, t);	
				}
			}
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
	}
	
	public void rename(String filename){
		this.filename = filename;
		File newFile = new File(filename);
		boolean success = this.logFile.renameTo(newFile);
//		if(success) System.out.println("yay");
		
		try {
			if(!logFile.exists()){
				this.logFile.createNewFile();	
			}
			this.fis = new FileInputStream(logFile);
			this.fos = new FileOutputStream(logFile, false);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void main(String [] args) {
		Log l = new Log("log.txt");
		Transaction t = new Transaction(Command.CreateDir, "./", "newDir", l.transactions.size());
		l.Start(t);
		
		l.AddMessage(t.toString());
		l.Commit(t);
		Transaction t1 = new Transaction(Command.CreateFile, "./", "newFile", l.transactions.size());
		l.Start(t1);

		l.AddMessage(t1.toString());
		l.Commit(t1);
//		
		l.Load();
	}
}
