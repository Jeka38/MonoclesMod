#!/bin/bash

SIZES="1 1.5 2 3 4"
EXPORT="../src/main/res/"
ICON="../Arcticons/scalable/apps"

for DIR in $(find -name "*.svg")
do
  FILE=${DIR##*/}
  NAME=${FILE%.*}
  cp ${FILE} ${FILE}.tmp
  rm ${FILE}.tmp
  #cp -f ${FILE} ${ICON}/${FILE}
  echo "Working on" ${FILE}
  for SIZE in ${SIZES}
  do
    svgexport ${NAME}.svg ${NAME}.png ${SIZE}x
    case ${SIZE} in
      1)
	mv ${NAME}.png ${EXPORT}/drawable-mdpi/
	;;
      1.5)
	mv ${NAME}.png ${EXPORT}/drawable-hdpi/
	;;
      2)
	mv ${NAME}.png ${EXPORT}/drawable-xhdpi/
	;;
      3)
	mv ${NAME}.png ${EXPORT}/drawable-xxhdpi/
	;;
      4)
	mv ${NAME}.png ${EXPORT}/drawable-xxxhdpi/
	;;
    esac
  done
done
