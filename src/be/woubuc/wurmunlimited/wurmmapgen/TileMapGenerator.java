package be.woubuc.wurmunlimited.wurmmapgen;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.Tiles.Tile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class TileMapGenerator {
	
	private ExecutorService executor;
	
	private int runningThreadsCount = 0;
	
	// Which tile types are seen as "path"
	private final int[] pathTiles = {
			Tiles.TILE_TYPE_COBBLESTONE,
			Tiles.TILE_TYPE_COBBLESTONE_ROUND,
			Tiles.TILE_TYPE_MARBLE_BRICKS,
			Tiles.TILE_TYPE_MARBLE_SLABS,
			Tiles.TILE_TYPE_SANDSTONE_BRICKS,
			Tiles.TILE_TYPE_SANDSTONE_SLABS,
			Tiles.TILE_TYPE_SLATE_BRICKS,
			Tiles.TILE_TYPE_SLATE_SLABS,
			Tiles.TILE_TYPE_STONE_SLABS};
	
	public MeshIO map;
	
	/**
	 * Opens the MeshIO connection required for the map builder
	 */
	public void openMap() throws IOException {
		Logger.title("Open Wurm MeshIO connection");
		map = MeshIO.open(WurmMapGen.fileManager.map_topLayer.getAbsolutePath());
		Logger.ok("MeshIO connection opened");
	}
	
	/**
	 * Closes the MeshIO connection
	 */
	public void closeMap() throws IOException {
		Logger.title("Close Wurm MeshIO connection");
		map.close();
		Logger.ok("Connection closed");
	}
	
	/**
	 * Generates the map tile images
	 */
	public void generateMapTiles() {
		Logger.title("Map generation");
		final long startTime = System.currentTimeMillis();
		
		Path imagesDirectory = Paths.get(WurmMapGen.properties.saveLocation.getAbsolutePath(), "images");
		Logger.details("Creating images directory " + imagesDirectory.toString());
		try {
			Files.createDirectories(imagesDirectory);
		} catch (IOException e) {
			Logger.error("Could not create directory");
			return;
		}
		Logger.ok("Images directory created");
		
		final int tileCount = (map.getSize() / WurmMapGen.properties.mapTileSize);
		final int totalProcesses = (tileCount * tileCount);
		
		// Use executor to generate each tile in a separate thread
		executor = Executors.newFixedThreadPool(WurmMapGen.properties.mapGeneratorThreads);
		
		Logger.details("Server map size: " + map.getSize() + " x " + map.getSize());
		Logger.details("Interactive map tile size: " + WurmMapGen.properties.mapTileSize + " x " + WurmMapGen.properties.mapTileSize);
		
		for (int x = 0; x < tileCount; x++) {
			for (int y = 0; y < tileCount; y++) {
				runningThreadsCount++;
				
				final int tileX = x;
				final int tileY = y;
				
				executor.execute(() -> {
					try {
						generateImageTile(tileX, tileY);
					} catch (Exception e) {
						Logger.error("Could not generate mape tile image\n      " + e.getMessage());
						e.printStackTrace();
						System.exit(1);
					}
					runningThreadsCount--;
				});
			}
		}
		executor.shutdown();
		
		Object obj = new Object();
		
		int lineLength = 0;
		long maxMemoryUsed = 0;
		
		while (!executor.isTerminated()) {
			// Display progress
			int percent = (int)((float)(totalProcesses - runningThreadsCount) / (float)(totalProcesses) * 100.0f);
			
			clearLine(lineLength);
			StringBuilder line = new StringBuilder();
			if (WurmMapGen.verbose) line.append("      -> ");
			line.append("Generating map tiles: ").append(percent).append("%");
			
			// In debug mode, also display memory use
			if (WurmMapGen.debug) {
				long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
				long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
				long memoryUsed = totalMemory - freeMemory;
				
				if (memoryUsed > maxMemoryUsed) maxMemoryUsed = memoryUsed;
				
				line.append(" (memory: ")
						.append(memoryUsed).append("mb used / ")
						.append(freeMemory).append("mb free)     \r");
			}
			
			lineLength = line.length();
			System.out.print(line.toString());
			
			try {
				synchronized (obj) {
					obj.wait(100);
				}
			} catch (InterruptedException ex) { }
		}
		
		// Tile generation completed
		StringBuilder line = new StringBuilder();
		line.append("Generated ").append(tileCount).append(" map tiles in ")
				.append(System.currentTimeMillis() - startTime).append("ms");
		
		if (WurmMapGen.debug) line.append(" (memory use max: ").append(maxMemoryUsed).append("mb)");
		line.append("     ");
		
		Logger.ok(line.toString());
	}
	
	private void clearLine(final int lineLength) {
		for (int i = 0; i <= lineLength; i++) {
			System.out.print("\b \b");
		}
	}
	
	/**
	 * Generates an image tile for the given tile coordinates
	 * @param  imageTileX  The X coordinate of the tile
	 * @param  imageTileY  The Y coordinate of the tile
	 */
	private void generateImageTile(final int imageTileX, final int imageTileY) throws IOException, SQLException {
		// Build the map tiles with a BufferedImage
		final BufferedImage imageTile = new BufferedImage(WurmMapGen.properties.mapTileSize, WurmMapGen.properties.mapTileSize, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D imageTileGraphics = imageTile.createGraphics();
		
		// Go over each Wurm tile
		final int offsetX = (imageTileX * WurmMapGen.properties.mapTileSize);
		final int offsetY = (imageTileY * WurmMapGen.properties.mapTileSize);
		
		for (int x = offsetX; x < offsetX + WurmMapGen.properties.mapTileSize; x++) {
			for (int y = offsetY; y < offsetY + WurmMapGen.properties.mapTileSize; y++) {
				
				// Load tile from Wurm map
				int tileEncoded = map.getTile(x, y);
				byte tileType = Tiles.decodeType(tileEncoded);
				short tileHeight = Tiles.decodeHeight(tileEncoded);
				
				Tile thisTile = Tiles.getTile(tileType);
				Color tileColor = thisTile.getColor();
				imageTileGraphics.setColor(tileColor);
				imageTileGraphics.fillRect(x - offsetX, y - offsetY, 1, 1);
				
				if (WurmMapGen.properties.mapGenerateShading) {
					
					// Figure out if we should shade this tile
					boolean shadeTile = true;
					if (!WurmMapGen.properties.mapShadePaths && IntStream.of(pathTiles).anyMatch(n -> n == tileType)) {
						shadeTile = false;
					}
					
					if (shadeTile && x < (map.getSize() - 1) && y < (map.getSize() - 1)) {
						short nextTileHeight = Tiles.decodeHeight(map.getTile(x + 1, y + 1));
						
						if (tileHeight - nextTileHeight > 0) {
							// Shade with black if the slope is towards the next tile (shadow side)
							int alpha = (int) Math.round(Math.pow(tileHeight - nextTileHeight, (0.95 * WurmMapGen.properties.mapShadingModifier)));
							if (alpha > 255) alpha = 255;
							
							imageTileGraphics.setColor(new Color(0, 0, 0, alpha));
							imageTileGraphics.fillRect(x - offsetX, y - offsetY, 1, 1);
							
						} else {
							// Shade with white if the slope is away from the next tile
							int alpha = (int) Math.round(Math.pow(nextTileHeight - tileHeight, (0.8 * WurmMapGen.properties.mapShadingModifier)));
							if (alpha > 255) alpha = 255;
							
							imageTileGraphics.setColor(new Color(255, 255, 255, alpha));
							imageTileGraphics.fillRect(x - offsetX, y - offsetY, 1, 1);
						}
					}
				}
				
				// Draw water
				if (WurmMapGen.properties.mapGenerateWater && tileHeight < 0) {
					imageTileGraphics.setColor(new Color(20,80,180,210));
					imageTileGraphics.fillRect(x - offsetX, y - offsetY, 1, 1);
				}
			}
		}
		
		if (WurmMapGen.properties.mapGenerateBridges) {
			
			// Set bridge colour based on cobblestone tiles (ID 9)
			Tile thisTile = Tiles.getTile(9);
			Color tileColor = thisTile.getColor();
			imageTileGraphics.setColor(tileColor);
			
			// Get bridge parts data from database
			PreparedStatement statement = WurmMapGen.db.getZones().getConnection()
					.prepareStatement("SELECT TILEX, TILEY FROM BRIDGEPARTS WHERE TILEX >= ? AND TILEY >= ? AND TILEX < ? AND TILEY < ?;");
			
			statement.setInt(1, offsetX);
			statement.setInt(2, offsetY);
			statement.setInt(4, offsetX + WurmMapGen.properties.mapTileSize);
			statement.setInt(4, offsetY + WurmMapGen.properties.mapTileSize);
			
			ResultSet resultSet = statement.executeQuery();
			
			while (resultSet.next()) {
				int tileX = resultSet.getInt("TILEX");
				int tileY = resultSet.getInt("TILEY");
				imageTileGraphics.fillRect(tileX - offsetX, tileY - offsetY, 1, 1);
			}
			
			resultSet.close();
			statement.close();
		}
		
		// Get output filename
		Path outputPath = Paths.get(WurmMapGen.properties.saveLocation.getAbsolutePath(), "images", imageTileX + "-" + imageTileY + ".png");
		
		// Write the generated image to a file
		ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
		ImageOutputStream outputStream = ImageIO.createImageOutputStream(outputPath.toFile());
		writer.setOutput(outputStream);
		
		ImageWriteParam param = new JPEGImageWriteParam(java.util.Locale.getDefault());
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT) ;
		param.setCompressionQuality(1f);
		
		writer.write(null, new IIOImage(imageTile, null, null ), param);
		writer.dispose();
		
		// Clear image buffer
		imageTileGraphics.dispose();
		imageTile.flush();
		
		// Tell JVM it should run garbage collection on the disposed image data
		System.gc();
	}
}
