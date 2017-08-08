package com.imraginbro.wurm.mapgen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.Tiles.Tile;

public class MapGen {

	public final static String newLine = System.lineSeparator();
	public final static String separator = java.io.File.separator;
	
	public static int threadCounter = 0;

	public static File map_topLayer = null;
	public static File db_wurmZones = null;
	public static File db_wurmItems = null;
	public static File db_wurmPlayers = null;

	public static File[] fileBackupArray = new File[4];

	//vars for map gen
	public static boolean gen_map_shading = true;
	public static boolean gen_map_shade_paths = true;
	public static boolean gen_map_water = true;
	public static boolean gen_map_bridges = true;
	
	//marker generation
	public static boolean showDeeds = true;
	public static boolean showGuardTowers = true;
	public static boolean showStructures = true;
	
	//config settings
	public static boolean replaceFiles = true;
	public static File wurmMapLocation = null;
	public static File saveLocation = null;

	public static void main(String[] args) throws Exception {
		
		if (!FileManagement.loadPropValues()) {
			return;
		}

		if (wurmMapLocation == null || saveLocation == null) {
			System.out.println("[ERROR] Wurm map location or save location was not set.");
			return;
		}

		map_topLayer = new File(wurmMapLocation.getAbsolutePath() + separator + "top_layer.map");
		db_wurmZones = new File(wurmMapLocation.getAbsolutePath() + separator + "sqlite" + separator + "wurmzones.db");
		db_wurmItems = new File(wurmMapLocation.getAbsolutePath() + separator + "sqlite" + separator + "wurmitems.db");
		db_wurmPlayers = new File(wurmMapLocation.getAbsolutePath() + separator + "sqlite" + separator + "wurmplayers.db");
		
		if (!map_topLayer.exists()) {
			System.out.println("[ERROR] Could not find top_layer.map! Stopping program.");
			return;
		}

		fileBackupArray[0] = map_topLayer;
		fileBackupArray[1] = db_wurmZones;
		fileBackupArray[2] = db_wurmItems;
		fileBackupArray[3] = db_wurmPlayers;

		final long startTime = System.currentTimeMillis();

		FileManagement.makeTempCopies(fileBackupArray, saveLocation);
		FileManagement.relocateFileVars();

		System.out.println("Loading top_layer.map file...");
		MeshIO map = MeshIO.open(map_topLayer.getAbsolutePath());

		genImages(map);
		map.close();
		
		FileManagement.extractRescources("/resources/required.zip", saveLocation);
		FileGeneration.generateFiles(map);

		System.out.println("Removing temporary files...");
		FileManagement.deleteDir(new File(saveLocation.getAbsolutePath() + separator + "tmp"));
		
		final long endTime = System.currentTimeMillis();
		final long totalTime = (endTime - startTime)/1000;
		System.out.println("Finished with map generation! " + totalTime + " seconds.");
	}

	public static void genImages(MeshIO map) throws IllegalArgumentException, ImagingOpException, IOException, SQLException, InterruptedException {
		System.out.println("Generating image for map");
		BufferedImage original = genMap(map, 1);
		genTileMap(original);
		original.flush();
	}
	
	public static BufferedImage genMap(MeshIO map, int PIXEL_SIZE) throws SQLException, InterruptedException {

		int MAP_SIZE = map.getSize();
		int IMAGE_SIZE = PIXEL_SIZE * MAP_SIZE;

		BufferedImage mapImage = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);

		ExecutorService executor = Executors.newFixedThreadPool(5);
		System.out.println("Starting multi-thread image generation");
		
		for (int y = 0; y < MAP_SIZE; y++) {
			threadCounter++;
			Runnable mt = new MapThreader(y, PIXEL_SIZE, map, mapImage);
			executor.execute(mt);
		}
		
		executor.shutdown();
		
		Object obj = new Object();
		
		while (!executor.isTerminated()) {
			int percent = (int)((float)(MAP_SIZE - threadCounter) / (float)MAP_SIZE * 100.0f);
			System.out.print("Completion percent: " + percent + "%\r");
			try {
				synchronized (obj) {
					obj.wait(1000);
				}
			} catch (InterruptedException ex) {
			}
		}
		threadCounter = 0;
		System.out.println("");
		System.out.println("Image generation complete");
		drawBridges(mapImage, PIXEL_SIZE);
		return mapImage;
	}
	
	public static class MapThreader implements Runnable {

		private final int y;
		private final int PIXEL_SIZE;
		private final MeshIO map;
		private final BufferedImage mapImage;

		MapThreader(int y, int PIXEL_SIZE, MeshIO map, BufferedImage mapImage) {
			this.y = y;
			this.PIXEL_SIZE = PIXEL_SIZE;
			this.map = map;
			this.mapImage = mapImage;
		}

		@Override
		public void run() {
			genMapThread(y, PIXEL_SIZE, map, mapImage);
		}
	}

	public static void genMapThread(int y, int PIXEL_SIZE, MeshIO map, BufferedImage mapImage) {
		Graphics2D g_mapImage = mapImage.createGraphics();
		for (int x = 0; x < map.getSize(); x++) {
			int newX = x * PIXEL_SIZE;
			int newY = y * PIXEL_SIZE;
			int tileEncoded = map.getTile(x, y);
			byte tileType = Tiles.decodeType(tileEncoded);
			short tileHeight = Tiles.decodeHeight(tileEncoded);
			Tile thisTile = Tiles.getTile(tileType);
			Color tileColor = thisTile.getColor();
			g_mapImage.setColor(tileColor);
			g_mapImage.fillRect(newX, newY, PIXEL_SIZE, PIXEL_SIZE);
			if (gen_map_shading) {
				boolean checkPath = false;
				if (!gen_map_shade_paths) {
					final int[] path_tile_types = {
							Tiles.TILE_TYPE_COBBLESTONE, Tiles.TILE_TYPE_COBBLESTONE_ROUND,
							Tiles.TILE_TYPE_MARBLE_BRICKS, Tiles.TILE_TYPE_MARBLE_SLABS,
							Tiles.TILE_TYPE_SANDSTONE_BRICKS, Tiles.TILE_TYPE_SANDSTONE_SLABS,
							Tiles.TILE_TYPE_SLATE_BRICKS, Tiles.TILE_TYPE_SLATE_SLABS,
							Tiles.TILE_TYPE_STONE_SLABS};
					checkPath = IntStream.of(path_tile_types).anyMatch(n -> n == tileType);
				}
				if (!checkPath) {
					if (x < (map.getSize()-1) && y < (map.getSize()-1)) {
						int lastTileEncoded = map.getTile(x + 1, y + 1);
						short nextTileHeight = Tiles.decodeHeight(lastTileEncoded);
						int calc = tileHeight - nextTileHeight;
						if (calc > 0) {
							int alpha = (int) Math.round(Math.pow(calc, 0.95));
							if (alpha > 255) {
								alpha = 255;
							}
							g_mapImage.setColor(new Color(0,0,0,alpha));
							g_mapImage.fillRect(newX, newY, PIXEL_SIZE, PIXEL_SIZE);
						} else {
							calc = -calc;
							int alpha = (int) Math.round(Math.pow(calc, 0.8));
							if (alpha > 255) {
								alpha = 255;
							}
							g_mapImage.setColor(new Color(255,255,255,alpha));
							g_mapImage.fillRect(newX, newY, PIXEL_SIZE, PIXEL_SIZE);
						}
					}
				}
			}
			if (gen_map_water && tileHeight < 0) {
				g_mapImage.setColor(new Color(20,80,180,210));
				g_mapImage.fillRect(newX, newY, PIXEL_SIZE, PIXEL_SIZE);
			}
		}
		g_mapImage.dispose();
		threadCounter--;
	}

	public static void drawBridges(BufferedImage mapImage, int PIXEL_SIZE) throws SQLException {
		if (gen_map_bridges) {
			if (!db_wurmZones.exists()) {
				System.out.println("[ERROR] Could not find zones.db. Skipping bridge generation.");
				return;
			}
			Graphics2D g_mapImage = mapImage.createGraphics();
			System.out.println("Loading bridges from wurmzones.db...");
			Tile thisTile = Tiles.getTile(9);
			Color tileColor = thisTile.getColor();
			g_mapImage.setColor(tileColor);
			Connection connection = DriverManager.getConnection("jdbc:sqlite:"+db_wurmZones);
			Statement statement = connection.createStatement();  
			ResultSet resultSet = statement.executeQuery("SELECT * FROM BRIDGEPARTS;"); 
			int count = 0;
			System.out.println("Drawing bridges...");
			while (resultSet.next()) {
				int tileX = resultSet.getInt("TILEX") * PIXEL_SIZE;
				int tileY = resultSet.getInt("TILEY") * PIXEL_SIZE;
				g_mapImage.fillRect(tileX, tileY, PIXEL_SIZE, PIXEL_SIZE);
				count++;
			}
			g_mapImage.dispose();
			connection.close();
			System.out.println("Finished drawing " + count + " bridge tiles");
		}
	}

	public static void genTileMap(BufferedImage img) throws IOException {
		System.out.println("Creating tile map from image...");
		int subImg = img.getWidth() / 256;
		ExecutorService executor = Executors.newFixedThreadPool(2);
		for (int x = 0; x < subImg; x++) {
			for (int y = 0; y < subImg; y++) {
				/*new File(saveLocation.getAbsolutePath() + separator + "images").mkdirs();
				BufferedImage newImg = img.getSubimage(x * 256, y * 256, 256, 256);
				FileManagement.saveToFile(newImg, new File(saveLocation.getAbsolutePath() + separator + "images" + separator + x + "-" + y + ".png"));
				newImg.flush();*/
				threadCounter++;
				Runnable mt = new MapTileThreader(x, y, img);
				executor.execute(mt);
			}
		}
		
		executor.shutdown();
		Object obj = new Object();
		
		while (!executor.isTerminated()) {
			int percent = (int)((float)((subImg*subImg) - threadCounter) / (float)(subImg*subImg) * 100.0f);
			System.out.print("Completion percent: " + percent + "%\r");
			try {
				synchronized (obj) {
					obj.wait(1000);
				}
			} catch (InterruptedException ex) {
			}
		}
		
		System.out.println("");
		img.flush();
		threadCounter = 0;
	}
	
	public static class MapTileThreader implements Runnable {

		private final int x;
		private final int y;
		private final BufferedImage img;

		MapTileThreader(int x, int y, BufferedImage img) {
			this.x = x;
			this.y = y;
			this.img = img;
		}

		@Override
		public void run() {
			new File(saveLocation.getAbsolutePath() + separator + "images").mkdirs();
			BufferedImage newImg = img.getSubimage(x * 256, y * 256, 256, 256);
			try {
				FileManagement.saveToFile(newImg, new File(saveLocation.getAbsolutePath() + separator + "images" + separator + x + "-" + y + ".png"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			newImg.flush();
			threadCounter--;
		}
	}

}


