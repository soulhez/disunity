/*
 ** 2013 June 16
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.unity.extract.handler;

import info.ata4.unity.enums.TextureFormat;
import info.ata4.unity.struct.asset.Texture2D;
import info.ata4.unity.struct.dds.DDSHeader;
import info.ata4.unity.struct.dds.DDSPixelFormat;
import info.ata4.util.io.ByteBufferInput;
import info.ata4.util.io.ByteBufferOutput;
import info.ata4.util.io.DataInputReader;
import info.ata4.util.io.DataOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class Texture2DHandler extends ExtractHandler {
    
    private static final Logger L = Logger.getLogger(Texture2DHandler.class.getName());
    
    private Texture2D t2d;
    private TextureFormat tf;
    
    @Override
    public String getClassName() {
        return "Texture2D";
    }

    @Override
    public void extract(ByteBuffer bb, int id) throws IOException {
        DataInputReader in = new DataInputReader(new ByteBufferInput(bb));
        
        t2d = new Texture2D(getAssetFormat());
        t2d.read(in);
        
        // some textures don't have any image data, not sure why...
        if (t2d.imageData.length == 0) {
            L.log(Level.WARNING, "Texture2D {0} is empty", t2d.name);
            return;
        }
        
        // get texture format
        tf = TextureFormat.fromOrdinal(t2d.textureFormat);
        if (tf == null) {
            L.log(Level.WARNING, "Texture2D {0} has unknown texture format {1}",
                    new Object[]{t2d.name, t2d.textureFormat});
            return;
        }
        
        // choose a fitting container format
        switch (tf) {
            case PVRTC_RGB2:
            case PVRTC_RGBA2:
            case PVRTC_RGB4:
            case PVRTC_RGBA4:
                extractPVR(id);
                break;
                
            case ATC_RGB4:
            case ATC_RGBA8:
                extractATC(id);
                break;
                
            default:
                extractDDS(id);
        }
    }
    
    private int getMipMapCount(int width, int height) {
        int mipMapCount = 1;
        for (int dim = Math.max(width, height); dim > 1; dim /= 2) {
            mipMapCount++;
        }
        return mipMapCount;
    }
    
    private void extractDDS(int id) throws IOException {
        DDSHeader dds = new DDSHeader();
        dds.dwWidth = t2d.width;
        dds.dwHeight = t2d.height;

        switch (tf) {
            case Alpha8:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_ALPHA;
                dds.ddspf.dwABitMask = 0xff;
                dds.ddspf.dwRGBBitCount = 8;
                break;
                
            case RGB24:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGB;
                dds.ddspf.dwRBitMask = 0xff0000;
                dds.ddspf.dwGBitMask = 0x00ff00;
                dds.ddspf.dwBBitMask = 0x0000ff;
                dds.ddspf.dwRGBBitCount = 24;
                break;
                
            case RGBA32:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGBA;
                dds.ddspf.dwRBitMask = 0x000000ff;
                dds.ddspf.dwGBitMask = 0x0000ff00;
                dds.ddspf.dwBBitMask = 0x00ff0000;
                dds.ddspf.dwABitMask = 0xff000000;
                dds.ddspf.dwRGBBitCount = 32;
                break;
                
            case BGRA32:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGBA;
                dds.ddspf.dwRBitMask = 0x00ff0000;
                dds.ddspf.dwGBitMask = 0x0000ff00;
                dds.ddspf.dwBBitMask = 0x000000ff;
                dds.ddspf.dwABitMask = 0xff000000;
                dds.ddspf.dwRGBBitCount = 32;
                break;
                
            case ARGB32:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGBA;
                dds.ddspf.dwRBitMask = 0x0000ff00;
                dds.ddspf.dwGBitMask = 0x00ff0000;
                dds.ddspf.dwBBitMask = 0xff000000;
                dds.ddspf.dwABitMask = 0x000000ff;
                dds.ddspf.dwRGBBitCount = 32;
                break;
                    
            case ARGB4444:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGBA;
                dds.ddspf.dwRBitMask = 0x0f00;
                dds.ddspf.dwGBitMask = 0x00f0;
                dds.ddspf.dwBBitMask = 0x000f;
                dds.ddspf.dwABitMask = 0xf000;
                dds.ddspf.dwRGBBitCount = 16;
                break;
                
            case RGB565:
                dds.ddspf.dwFlags = DDSPixelFormat.DDPF_RGB;
                dds.ddspf.dwRBitMask = 0xf800;
                dds.ddspf.dwGBitMask = 0x07e0;
                dds.ddspf.dwBBitMask = 0x001f;
                dds.ddspf.dwRGBBitCount = 16;
                break;
            
            case DXT1:
                dds.ddspf.dwFourCC = DDSPixelFormat.PF_DXT1;
                break;
            
            case DXT5:
                dds.ddspf.dwFourCC = DDSPixelFormat.PF_DXT5; 
                break;
                             
            default:
                L.log(Level.WARNING, "Texture2D {0} has unsupported texture format {1}",
                        new Object[] {t2d.name, tf});
                return;
        }

        // set mip map flags if required
        if (t2d.mipMap) {
            dds.dwFlags |= DDSHeader.DDS_HEADER_FLAGS_MIPMAP;
            dds.dwCaps |= DDSHeader.DDS_SURFACE_FLAGS_MIPMAP;
            dds.dwMipMapCount = getMipMapCount(dds.dwWidth, dds.dwHeight);
        }
        
        // set and calculate linear size
        dds.dwFlags |= DDSHeader.DDS_HEADER_FLAGS_LINEARSIZE;
        if (dds.ddspf.dwFourCC != 0) {
            dds.dwPitchOrLinearSize = dds.dwWidth * dds.dwHeight;
            
            if (tf == TextureFormat.DXT1) {
                dds.dwPitchOrLinearSize /= 2;
            }
            
            dds.ddspf.dwFlags |= DDSPixelFormat.DDPF_FOURCC;
        } else {
            dds.dwPitchOrLinearSize = (t2d.width * t2d.height * dds.ddspf.dwRGBBitCount) / 8;
        }
        
        // TODO: convert AG to RGB normal maps? (colorSpace = 0)
        
        ByteBuffer bbTex = ByteBuffer.allocateDirect(128 + t2d.imageData.length);
        bbTex.order(ByteOrder.LITTLE_ENDIAN);
        
        // write header
        DataOutputWriter out = new DataOutputWriter(new ByteBufferOutput(bbTex));
        dds.write(out);
        
        // write data
        ByteBuffer bbRaw = ByteBuffer.wrap(t2d.imageData);
        bbTex.put(bbRaw);
        
        bbTex.rewind();
        
        extractToFile(bbTex, id, t2d.name, "dds");
    }

    private void extractPVR(int id) {
        // TODO
        throw new UnsupportedOperationException("PVR not yet implemented");
    }
    
    private void extractATC(int id) {
        // TODO
        throw new UnsupportedOperationException("ATC not yet implemented");
    }
}