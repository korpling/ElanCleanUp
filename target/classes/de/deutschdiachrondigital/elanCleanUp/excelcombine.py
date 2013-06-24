import glob, codecs

fl = glob.glob("/media/sf_shared_folder/DDDcorpora/KONVERTIERUNGSPLACE/Genesis/2-excel/file-by-file/*.csv")

for f in fl:
  fin = codecs.open(f, "r", "utf-8")
  lines = fin.readlines()
  fin.close()

  for line in lines:
    print line
    raw_input()
