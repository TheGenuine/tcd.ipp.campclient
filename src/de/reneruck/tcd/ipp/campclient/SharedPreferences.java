package de.reneruck.tcd.ipp.campclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

public class SharedPreferences {

	private File storedData = new File("sharedPreferences.pref");
	private Map<Object, Object> inmemory = new HashMap<Object, Object>();
	
	public SharedPreferences() {
		if(!this.storedData.exists()){
			try {
				this.storedData.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		getPersistedData();
	}
	
	public Map<?, ?> getAll() {
		return new HashMap<Object, Object>(this.inmemory);
	}

	public boolean contains(String key) {
		return this.inmemory.containsKey(key);
	}

	public void remove(String key) {
		this.inmemory.remove(key);
	}

	public void putString(String key, String value) {
		this.inmemory.put(key, value);
	}

	public void apply() {
		try {
			PrintWriter writer = new PrintWriter(storedData);
			writer.print("");
			writer.close();
			this.storedData.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			persistData();
	}

	private void getPersistedData() {
		FileInputStream fis;
		try {
			fis = new FileInputStream(this.storedData);
			List<String> readLines = IOUtils.readLines(fis, "UTF-8");
			for (String string : readLines) {
				String[] split = string.split("~");
				if(split.length > 1) {
					this.inmemory.put(split[0], split[1]);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void persistData() {
		try {
			Set<Object> keySet = this.inmemory.keySet();
			FileOutputStream fos = new FileOutputStream(this.storedData);
			
			for (Object key : keySet) {
				String input = key + "~" + this.inmemory.get(key) + "\n";
				IOUtils.write(new String(input.getBytes(), "UTF-8"), fos);
			}
			IOUtils.closeQuietly(fos);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void put(Object key, Object value) {
		this.inmemory.put(key, value);
	}

	public String getString(String key, String defaultValue) {
		if(this.inmemory.containsKey(key)) {
			return (String) this.inmemory.get(key);
		} else {
			return defaultValue;
		}
	}

	public void commit() {
		apply();
	}

}
