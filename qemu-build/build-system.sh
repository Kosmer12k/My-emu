#!/bin/sh
# Виконується всередині контейнера Alpine (aarch64).
# Збирає статичний qemu-system-i386 (для Android-x86) і забирає з нього файли BIOS.
set -eux

VER=8.2.4

apk add --no-cache build-base python3 py3-setuptools ninja pkgconf bash curl xz \
  flex bison perl linux-headers \
  glib-dev glib-static pcre2-dev pcre2-static libffi-dev \
  pixman-dev pixman-static zlib-dev zlib-static \
  gettext-dev gettext-static

cd /tmp
curl -fsSLO "https://download.qemu.org/qemu-$VER.tar.xz"
tar xf "qemu-$VER.tar.xz"
cd "qemu-$VER"

mkdir build
cd build
../configure \
  --static \
  --target-list=i386-softmmu \
  --disable-docs --disable-tools --disable-guest-agent \
  --disable-werror --disable-user --disable-linux-user \
  --disable-gtk --disable-sdl --disable-opengl --disable-curses \
  --disable-gnutls --disable-nettle --disable-gcrypt \
  --disable-slirp --disable-vnc-sasl --disable-vnc-jpeg \
  --enable-vnc

ninja -j"$(nproc)" qemu-system-i386

strip qemu-system-i386
cp qemu-system-i386 /out/qemu-system-i386

# BIOS та інші файли, без яких x86-машина не стартує
mkdir -p /out/pc-bios
cd "/tmp/qemu-$VER/pc-bios"
for f in bios-256k.bin bios.bin vgabios.bin vgabios-stdvga.bin kvmvapic.bin linuxboot_dma.bin linuxboot.bin; do
  if [ -f "$f" ]; then cp "$f" /out/pc-bios/; fi
done
cp -r keymaps /out/pc-bios/
ls -l /out/pc-bios

/tmp/qemu-$VER/build/qemu-system-i386 --version | tee /out/VERSION.txt
sha256sum /out/qemu-system-i386 | tee /out/SHA256.txt
