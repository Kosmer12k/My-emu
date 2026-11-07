#!/bin/bash
set -e

# Обновляем репозитории и устанавливаем необходимые зависимости для Meson/QEMU в Alpine
echo "==> Установка зависимостей сборки в Alpine..."
apk update
apk add --no-cache git dtc bash python3 py3-pip meson ninja gcc g++ make glib-dev pixman-dev linux-headers

# Переходим к конфигурации и сборке
echo "==> Запуск конфигурации QEMU..."

# Сюда автоматически подставятся ваши оригинальные команды конфигурации, например:
# ./configure --target-list=i386-softmmu ...
# make -j$(nproc)
