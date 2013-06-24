import glob, codecs

def writeout(l, p, fname):
  fout = codecs.open(p + "/" + fname, "w", "utf-8")
  fout.write( "".join(l))
  fout.close()

path = "/media/sf_shared_folder/DDDcorpora/KONVERTIERUNGSPLACE/Genesis/2-excel"
fl = glob.glob(path + "/file-by-file/*.csv")

outlines = []

j = 1
k = 1
for f in fl:
  fname = f.split("/")[-1].rstrip(".csv")
  fin = codecs.open(f, "r", "utf-8")
  lines = fin.readlines()
  fin.close()
  if len(outlines) == 0:
    outlines.append("fname;id;" + lines[0])
  i = 1
  for line in lines[1:]:
    line = fname + ";" + str(i) + ";" + line
    outlines.append(line)
    i = i + 1
  if j % 4 == 0:
    writeout(outlines, path, "tmp_excel" + str(k) + ".csv")
    outlines = []
    k = k + 1
  j = j + 1

if len(outlines) > 0:
  writeout(outlines, path, "tmp_excel" + str(k) + ".csv")
