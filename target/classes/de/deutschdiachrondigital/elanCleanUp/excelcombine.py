import glob, codecs

def clean(l):
  out = []
  for c in l:
    out.append(c)
  return "".join(out)

def writeout(l, p, fname):
  fout = codecs.open(p + "/" + fname, "w", "utf-8")
  fout.write( u"\n".join(l))
  fout.close()

path = "/media/sf_shared_folder/DDDcorpora/KONVERTIERUNGSPLACE/Physiologus/"
fl = glob.glob(path + "3_excel/file-by-file/*.csv")

outlines = []

j = 1
k = 1
for f in fl:
  fname = f.split("/")[-1].rstrip(".csv")
  fin = codecs.open(f, "r", "utf-8")
  lines = fin.read().split(u"\n")
  fin.close()
  if len(outlines) == 0:
    outlines.append(u"fname;id;" + lines[0])
  i = 1
  for line in lines[1:]:
    nline = fname.decode("utf-8")
    nline = nline + u";" + str(i) + u";"
    nline = nline + clean(line)
    outlines.append(nline)
    i = i + 1
  if len(outlines) >= 4000:
    writeout(outlines, path, "tmp_excel" + str(k) + ".csv")
    outlines = []
    k = k + 1
  j = j + 1

if len(outlines) > 0:
  writeout(outlines, path, "tmp_excel" + str(k) + ".csv")
