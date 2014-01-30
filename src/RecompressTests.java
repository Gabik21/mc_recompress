import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.jnbt.CompoundTag;
import org.jnbt.NBTInputStream;
import org.jnbt.Tag;
import org.junit.Rule;
import org.junit.Test;


public class RecompressTests {
	@Test
	public void testParseMCA() throws IOException {
		RandomAccessFile testFile = new RandomAccessFile("./assets/test.mca", "r");
    	FileChannel inChan = testFile.getChannel();
    	ByteBuffer buf = ByteBuffer.allocate((int) testFile.length());
    	inChan.read(buf);
    	buf.flip();
    	RegionFile region = RegionFile.parse(buf.array());
    	int chunkCount = 0;
    	for (int x = 0; x < 32; x++) {
    		for (int z = 0; z < 32; z++) {
    			if (region.getChunk(x, z) != null) {
    				chunkCount++;
    			}
    		}
    	}
    	// Ruby code used to check - 349 chunks in the test.mca region file
    	assertEquals(349, chunkCount);
	}
	
	@Test
	public void testReadChunks() throws IOException {
		RandomAccessFile testFile = new RandomAccessFile("./assets/test.mca", "r");
    	FileChannel inChan = testFile.getChannel();
    	ByteBuffer buf = ByteBuffer.allocate((int) testFile.length());
    	inChan.read(buf);
    	buf.flip();
    	RegionFile region = RegionFile.parse(buf.array());
    	
    	ChunkData testChunk = region.getChunk(16, 27); 
    	assertNotNull(testChunk);
    	SectionData bedrock = testChunk.getSection(0);
    	assertNotNull(bedrock);
    	byte[] blocks = bedrock.getBlocks();
    	assertNotNull(blocks);
    	
    	// Check for bedrock ID
    	assertEquals(7, blocks[0]);
	}
	
	@Test
	public void testRegenerateRegionFile() throws IOException {
		// Read test.mca as master copy
		RandomAccessFile testFile = new RandomAccessFile("./assets/test.mca", "r");
    	FileChannel inChan = testFile.getChannel();
    	ByteBuffer buf = ByteBuffer.allocate((int) testFile.length());
    	inChan.read(buf);
    	buf.flip();
    	RegionFile region = RegionFile.parse(buf.array());
    	
    	// Read test_converted.mci.gz - should read to the same data
		File archiveFile = new File("./assets/test_converted.mci.gz");
		byte[] fileData = Files.readAllBytes(archiveFile.toPath());
    	NBTInputStream reparser = new NBTInputStream(new ByteArrayInputStream(fileData));
    	Tag root = reparser.readTag();

    	assertEquals("Region", root.getName());
    	assertEquals("1.0", ((CompoundTag) root).getValue().get("MRI Version").getValue());

    	RegionFile reregion = RegionFile.fromArchive(root);
    	
    	compareRegions(region, reregion);
	}
	
	@Test
	public void testConvertReadBack() throws IOException {
		RandomAccessFile testFile = new RandomAccessFile("./assets/test.mca", "r");
    	FileChannel inChan = testFile.getChannel();
    	ByteBuffer buf = ByteBuffer.allocate((int) testFile.length());
    	inChan.read(buf);
    	buf.flip();
    	RegionFile region = RegionFile.parse(buf.array());
    	
    	File tempFile = File.createTempFile("test", ".mci.gz");
    	tempFile.deleteOnExit();
    	region.writeArchive(tempFile);
    	
    	byte[] fileData = Files.readAllBytes(tempFile.toPath());
    	NBTInputStream reparser = new NBTInputStream(new ByteArrayInputStream(fileData));
    	Tag root = reparser.readTag();

    	assertEquals("Region", root.getName());
    	assertEquals("1.0", ((CompoundTag) root).getValue().get("MRI Version").getValue());

    	RegionFile reregion = RegionFile.fromArchive(root);
    	// Reload original region
    	region = RegionFile.parse(buf.array());
    	
    	compareRegions(region, reregion);
	}
	
	@Test
	public void testTranscribeAssets() throws IOException {
    	Path tempDir = Files.createTempDirectory("temp_mca");
    	
    	Path assetPath = FileSystems.getDefault().getPath("assets/test.mca");
    	Files.copy(assetPath, tempDir.resolve("test.mca"));
    	
		MCAConverter converter = new MCAConverter(tempDir);
		converter.convertMCAFiles();

		// File should have been converted and original deleted
		assertTrue(Files.exists(tempDir.resolve("test.mri.gz")));
		assertFalse(Files.exists(tempDir.resolve("test.mca")));
		
		// Clean up
		Files.deleteIfExists(tempDir.resolve("test.mri.gz"));
		Files.delete(tempDir);
	}
	
	private void compareRegions(RegionFile region, RegionFile reregion) {
    	for (int x = 0; x < 32; x++) {
    		for (int z = 0; z < 32; z++) {
    			ChunkData c1 = region.getChunk(x, z);
    			ChunkData c2 = reregion.getChunk(x, z);
    			assertEquals(c1 == null, c2 == null);
    			
    			if (c1 != null) {
	    			for (int y = 0; y < 16; ++y) {
	    				boolean c1HasSection = (c1.getSection(y) != null);
	    				boolean c2HasSection = (c2.getSection(y) != null);
	    				assertEquals(c1HasSection, c2HasSection);
	    				
	    				if (c1HasSection) {
		    				for (String combineKey: RegionFile.combineBlockLengths.keySet()) {
			    				// Pick a pseudo-random byte to look at
			    				Random random = new Random(x);
			    				random = new Random(random.nextInt() + z);
			    				random = new Random(random.nextInt() + y);
			    				
			    				// Sample 50 random bytes, check identical
			    				for (int rep = 0; rep < 50; ++rep) {
				    				int selection = random.nextInt(RegionFile.combineBlockLengths.get(combineKey));
				    				byte[] c1data = c1.getSection(y).getByteBlock(combineKey);
				    				byte[] c2data = c2.getSection(y).getByteBlock(combineKey);
				    				assertEquals(c1data == null, c2data == null);
				    				if (c1data != null) {
				    					assertEquals(c1data[selection], c2data[selection]);
				    				}
			    				}
		    				}
	    				}
	    			}
    			}
    		}
    	}
	}
}
