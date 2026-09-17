#!/bin/sh
set -e

convert -background none A_international_vehicle_registration_oval.svg -resize 66x66 ../../res/drawable-nodpi/vehicle_a.png
convert -background none CH_international_vehicle_registration_oval.svg -resize 66x66 ../../res/drawable-nodpi/vehicle_ch.png
convert -background none  D_international_vehicle_registration_oval.svg -resize 66x66 ../../res/drawable-nodpi/vehicle_d.png
convert -background none                                         XW.svg -resize 66x66 ../../res/drawable-nodpi/vehicle_xw.png
