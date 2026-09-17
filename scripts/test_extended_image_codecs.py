import importlib.util
import io
import os
import sys
import subprocess
import tempfile
from pathlib import Path
import unittest
from PIL import Image
import pillow_heif

ROOT=Path(__file__).resolve().parents[1]
MODULE=Path(os.environ.get('ERP_IMAGE_CODEC_MODULE',str(ROOT/'erp-common/erp-common-core/src/main/resources/image/normalize_image.py')))
spec=importlib.util.spec_from_file_location('codec',MODULE)
codec=importlib.util.module_from_spec(spec);spec.loader.exec_module(codec)

class ExtendedImageTests(unittest.TestCase):
    def image(self,alpha=False):
        im=Image.new('RGBA' if alpha else 'RGB',(160,120))
        for y in range(im.height):
            for x in range(im.width):
                pixel=((x*7+y*3)%256,(x*11+y*5)%256,(x*13+y*7)%256)
                im.putpixel((x,y),pixel+((x*3)%256,) if alpha else pixel)
        return im
    def encode(self,ext,alpha=False,multiple=False):
        image=self.image(alpha);out=io.BytesIO();options={'quality':100,'save_all':True}
        if multiple:
            other=image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            options.update(append_images=[other],duration=[110,230],loop=3)
            if ext!='webp':options['primary_index']=1
        image.save(out,format='WEBP' if ext=='webp' else 'HEIF',**options)
        return out.getvalue()
    def check_roundtrip(self,ext,alpha=False,multiple=False):
        original=self.encode(ext,alpha,multiple);result,reason=codec.normalize(original,ext)
        a,_=codec.read_frames(original,ext);b,_=codec.read_frames(result,ext)
        self.assertEqual(codec.signature(a,ext),codec.signature(b,ext))
        self.assertLessEqual(len(result),len(original))
        return original,result,reason
    def test_webp_optimized_without_format_change(self):
        a,b,_=self.check_roundtrip('webp');self.assertLess(len(b),len(a))
    def test_webp_alpha(self):self.check_roundtrip('webp',True)
    def test_webp_animation_timing_and_loop(self):
        a,b,_=self.check_roundtrip('webp',True,True)
        self.assertEqual(Image.open(io.BytesIO(a)).info.get('loop'),Image.open(io.BytesIO(b)).info.get('loop'))
    def test_heic_optimized_without_format_change(self):
        a,b,_=self.check_roundtrip('heic');self.assertLess(len(b),len(a))
    def test_heif_optimized_without_format_change(self):self.check_roundtrip('heif')
    def test_heif_alpha(self):self.check_roundtrip('heif',True)
    def test_heif_multiple_images_keep_primary(self):self.check_roundtrip('heif',False,True)
    def test_invalid_image_and_mismatched_format_rejected(self):
        with self.assertRaises(Exception):codec.normalize(b'not an image','heic')
        with self.assertRaises(ValueError):codec.normalize(self.encode('webp'),'heif')
    def test_truncated_payload_rejected(self):
        for ext in ['webp','heic']:
            with self.assertRaises(Exception):codec.normalize(self.encode(ext)[:100],ext)
    def test_native_command_resource_limits(self):
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'a.heic';output=Path(directory)/'b.heic';source.write_bytes(self.encode('heic'))
            result=subprocess.run([sys.executable,'-I',str(MODULE),'--input',str(source),'--output',str(output),'--format','heic'],capture_output=True,text=True,timeout=35)
            self.assertEqual(0,result.returncode,result.stderr)
            self.assertLessEqual(output.stat().st_size,source.stat().st_size)
    def test_webp_exif_orientation(self):
        im=self.image();exif=Image.Exif();exif[274]=6;out=io.BytesIO();im.save(out,format='WEBP',quality=100,exif=exif)
        result,_=codec.normalize(out.getvalue(),'webp');frames,_=codec.read_frames(result,'webp')
        self.assertEqual((120,160),frames[0].size)

if __name__=='__main__':unittest.main()
