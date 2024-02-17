#!/bin/bash

# 生成する行数
lines=100000

# 出力ファイル名
output_file="output.txt"

# テキスト生成関数
generate_text() {
    # ここで適当なランダムな文字列を生成する方法を指定
    # 以下はランダムな8文字の文字列を生成する例
    echo "$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 8 | head -n 1)"
}

# ファイル生成
for ((i=1; i<=$lines; i++)); do
    text=$(generate_text)
    echo "$text" >> "$output_file"
done

echo "生成が完了しました。"
