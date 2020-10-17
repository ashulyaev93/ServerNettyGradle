package io.netty.example.file;/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

//package io.netty.example.file;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;

import java.io.RandomAccessFile;
import java.io.File;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServerHandler extends SimpleChannelInboundHandler<String> {

    private String currentFileName = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush("HELLO: Type the path of the file to retrieve.\n");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        RandomAccessFile raf = null;
        long length = -1;
        String action = null;
        //String fileName = null;
        //System.out.println(msg);
        try {
            String[] args = msg.split("\\s+");
            if(args.length != 2) throw new Exception("ERR: The command should contain just 2 arguments.");
            action = args[0].trim();
            this.currentFileName = args[1].trim();
            //System.out.println(action);
            //System.out.println(fileName);
            if(!action.matches("^(upload|download)$")) throw new Exception("ERR: The command should start with \"upload\" or \"download\" words.");
            if(!this.currentFileName.matches("^.*\\.txt$")) throw new Exception("ERR: The serer accepts only *.txt files.");
            Path path = Paths.get(this.currentFileName);
            if(action.equals("download") && !Files.isRegularFile(path)) throw new Exception("The file does not exist.");
            raf = new RandomAccessFile(this.currentFileName, action.equals("download") ? "r" : "rw");
            length = raf.length();
        } catch (Exception e) {
            ctx.writeAndFlush("ERR: " + e.getClass().getSimpleName() + ": " + e.getMessage() + '\n');
            return;
        } finally {
            if (length < 0 && raf != null) {
                raf.close();
            }
        }

        if(action.equals("download")) {
            ctx.write("OK, downloading: " + raf.length() + '\n');
            if (ctx.pipeline().get(SslHandler.class) == null) {
                // SSL not enabled - can use zero-copy file transfer.
                ctx.write(new DefaultFileRegion(raf.getChannel(), 0, length));
            } else {
                // SSL enabled - cannot use zero-copy file transfer.
                ctx.write(new ChunkedFile(raf));
            }
        }
        else {
            ctx.write("OK, uploading: " + raf.length() + '\n');
        }
        ctx.writeAndFlush("\n");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if(msg instanceof String){
            System.out.println("Here we are: " + (String)msg);
            channelRead0(ctx, (String) msg);
            return;
        }
        if(this.currentFileName == null){
            ctx.writeAndFlush("The fileName is empty\n");
            return;
        }
        File file = new File(currentFileName);//remember to change dest

        if (!file.exists()) {
            file.createNewFile();
        }

        ByteBuf byteBuf = (ByteBuf) msg;
        ByteBuffer byteBuffer = byteBuf.nioBuffer();
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();

        while (byteBuffer.hasRemaining()){;
            fileChannel.position(file.length());
            fileChannel.write(byteBuffer);
        }

        byteBuf.release();
        fileChannel.close();
        randomAccessFile.close();

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();

        if (ctx.channel().isActive()) {
            ctx.writeAndFlush("ERR: " +
                    cause.getClass().getSimpleName() + ": " +
                    cause.getMessage() + '\n').addListener(ChannelFutureListener.CLOSE);
        }
    }
}

